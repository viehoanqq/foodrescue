package com.foodrescue.order.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import com.foodrescue.order.infrastructure.messaging.RabbitConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
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
    private final ObjectMapper json;
    private final SpringDataOrderRepository orderRepository;
    private final SpringDataOrderItemRepository orderItemRepository;
    private final IOutboxPort outboxPort;

    public RefundStatusConsumer(
            JdbcTemplate jdbc,
            ObjectMapper json,
            SpringDataOrderRepository orderRepository,
            SpringDataOrderItemRepository orderItemRepository,
            IOutboxPort outboxPort) {
        this.jdbc = jdbc;
        this.json = json;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.outboxPort = outboxPort;
    }

    @RabbitListener(queues = RabbitConfiguration.ORDER_REFUND_STATUS_QUEUE)
    @Transactional
    public void receive(String message) throws Exception {
        JsonNode event = json.readTree(message);
        String eventIdStr = event.path("eventId").asText();
        String eventType = event.path("eventType").asText();
        if (eventIdStr.isBlank() || eventType.isBlank()) {
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

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order not found for refund status: {}", orderId);
            return;
        }

        UUID correlationId = event.path("correlationId").isTextual()
                ? UUID.fromString(event.path("correlationId").asText())
                : UUID.randomUUID();

        if ("refund.requested.v1".equals(eventType)) {
            order.setPaymentStatus("REFUND_PENDING");
            order.setAggregateVersion(order.getAggregateVersion() + 1);
            orderRepository.save(order);
            emitOrderStateChanged(order, correlationId);
            log.info("Order {} updated paymentStatus to REFUND_PENDING", order.getOrderCode());
        } else if ("refund.completed.v1".equals(eventType)) {
            order.setPaymentStatus("REFUNDED");
            order.setAggregateVersion(order.getAggregateVersion() + 1);
            orderRepository.save(order);
            emitOrderStateChanged(order, correlationId);
            log.info("Order {} updated paymentStatus to REFUNDED", order.getOrderCode());
        }
    }

    private void emitOrderStateChanged(OrderEntity order, UUID correlationId) {
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(order.getId());
        Map<String, Object> statePayload = new LinkedHashMap<>();
        statePayload.put("orderId", order.getId());
        statePayload.put("storeId", order.getStoreId());
        statePayload.put("customerId", order.getCustomerId());
        statePayload.put("status", order.getStatus());
        statePayload.put("paymentStatus", order.getPaymentStatus());
        statePayload.put("totalAmount", order.getTotalAmount().longValue());
        statePayload.put("commissionAmount", order.getCommissionAmount().longValue());
        statePayload.put("storeEarning", order.getStoreEarning().longValue());
        statePayload.put("completedAt", order.getCompletedAt() != null ? order.getCompletedAt().toString() : null);
        statePayload.put("reservationId", order.getReservationId() != null ? order.getReservationId().toString() : null);
        statePayload.put("items", items.stream()
                .map(it -> Map.of("batchId", it.getBatchId(), "quantity", it.getQuantity()))
                .toList());

        outboxPort.append(EventEnvelope.create(
                "order.state-changed.v1",
                "Order",
                order.getId().toString(),
                order.getAggregateVersion(),
                correlationId,
                null,
                null,
                statePayload));
    }
}
