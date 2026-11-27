package com.foodrescue.order.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.order.application.usecase.OrderSupport;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.messaging.RabbitConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RefundStatusConsumer {
    private static final Logger log = LoggerFactory.getLogger(RefundStatusConsumer.class);
    private static final String CONSUMER_NAME = "order.refund-status";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final SpringDataOrderRepository orderRepository;
    private final OrderSupport orderSupport;

    public RefundStatusConsumer(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            SpringDataOrderRepository orderRepository,
            OrderSupport orderSupport) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.orderSupport = orderSupport;
    }

    @RabbitListener(queues = RabbitConfiguration.ORDER_REFUND_STATUS_QUEUE)
    @Transactional
    public void onMessage(String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String eventIdStr = event.path("eventId").asText(null);
        String eventType = event.path("eventType").asText("");
        if (eventIdStr == null || eventIdStr.isBlank()) {
            log.warn("Invalid message envelope: {}", message);
            return;
        }

        UUID eventId = UUID.fromString(eventIdStr);

        // Inbox deduplication
        int inserted = jdbc.update(
                "INSERT IGNORE INTO inbox_events (consumer_name, event_id, processed_at) VALUES (?, ?, ?)",
                CONSUMER_NAME, eventId.toString(), Timestamp.from(Instant.now()));
        if (inserted == 0) {
            log.info("Duplicate event ignored in refund-status: {}", eventId);
            return;
        }

        JsonNode payload = event.path("payload");
        long orderId = payload.path("orderId").asLong();
        if (orderId <= 0) {
            return;
        }

        OrderEntity order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null) {
            log.warn("Order not found for refund status: {}", orderId);
            return;
        }

        UUID correlationId = event.path("correlationId").isTextual()
                ? UUID.fromString(event.path("correlationId").asText())
                : UUID.randomUUID();

        if ("refund.requested.v1".equals(eventType)) {
            if (!"REFUNDED".equalsIgnoreCase(order.getPaymentStatus())) {
                order.setPaymentStatus("REFUND_PENDING");
                order.setAggregateVersion(order.getAggregateVersion() + 1);
                orderRepository.save(order);
                orderSupport.emitOrderStateChanged(order, null, correlationId, null);
                log.info("Order {} updated paymentStatus to REFUND_PENDING", order.getOrderCode());
            }
        } else if ("refund.completed.v1".equals(eventType)) {
            order.setPaymentStatus("REFUNDED");
            order.setAggregateVersion(order.getAggregateVersion() + 1);
            orderRepository.save(order);
            orderSupport.emitOrderStateChanged(order, null, correlationId, null);
            log.info("Order {} updated paymentStatus to REFUNDED", order.getOrderCode());
        }
    }
}
