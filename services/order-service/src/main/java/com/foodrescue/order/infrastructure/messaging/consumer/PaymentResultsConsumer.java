package com.foodrescue.order.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import com.foodrescue.order.infrastructure.messaging.RabbitConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentResultsConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentResultsConsumer.class);
    private static final String CONSUMER_NAME = "order.payment-results";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SpringDataOrderRepository orderRepository;
    private final SpringDataOrderItemRepository orderItemRepository;
    private final IInventoryPort inventoryPort;
    private final IOutboxPort outboxPort;

    public PaymentResultsConsumer(
            JdbcTemplate jdbc,
            ObjectMapper json,
            SpringDataOrderRepository orderRepository,
            SpringDataOrderItemRepository orderItemRepository,
            IInventoryPort inventoryPort,
            IOutboxPort outboxPort) {
        this.jdbc = jdbc;
        this.json = json;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryPort = inventoryPort;
        this.outboxPort = outboxPort;
    }

    @RabbitListener(queues = RabbitConfiguration.ORDER_PAYMENT_RESULTS_QUEUE)
    @Transactional
    public void receive(String message) throws Exception {
        JsonNode event = json.readTree(message);
        String eventIdStr = event.path("eventId").asText();
        String eventType = event.path("eventType").asText();
        if (eventIdStr.isBlank() || eventType.isBlank()) {
            log.warn("Invalid message envelope: {}", message);
            return;
        }

        UUID eventId = UUID.fromString(eventIdStr);

        // Inbox deduplication
        int inserted = jdbc.update(
                "INSERT IGNORE INTO inbox_events (consumer_name, event_id, processed_at) VALUES (?, ?, ?)",
                CONSUMER_NAME, eventId.toString(), Timestamp.from(Instant.now()));
        if (inserted == 0) {
            log.info("Duplicate event ignored: {}", eventId);
            return;
        }

        JsonNode payload = event.path("payload");
        long orderId = payload.path("orderId").asLong();
        if (orderId <= 0) {
            return;
        }

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order not found for payment result: {}", orderId);
            return;
        }

        UUID correlationId = event.path("correlationId").isTextual()
                ? UUID.fromString(event.path("correlationId").asText())
                : UUID.randomUUID();

        if ("payment.succeeded.v1".equals(eventType)) {
            String status = order.getStatus();
            if ("PENDING_PAYMENT".equalsIgnoreCase(status)) {
                order.setStatus("CONFIRMED");
                order.setPaymentStatus("PAID");
                order.setAggregateVersion(order.getAggregateVersion() + 1);
                orderRepository.save(order);

                emitOrderStateChanged(order, correlationId);

                // Audit log
                Map<String, Object> auditPayload = Map.of(
                        "action", "PAYMENT_SUCCESS",
                        "entityType", "ORDER",
                        "entityId", order.getId().toString(),
                        "amount", order.getTotalAmount().longValue(),
                        "detail", "VNPay payment successful"
                );
                outboxPort.append(EventEnvelope.create("audit.action.v1", "Order", order.getId().toString(),
                        order.getAggregateVersion(), correlationId, null, order.getCustomerId(), auditPayload));

                log.info("Order {} confirmed by payment success", order.getOrderCode());
            } else if ("CANCELLED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)) {
                // Payment received after cancel/expired -> trigger refund required
                order.setPaymentStatus("REFUND_PENDING");
                order.setAggregateVersion(order.getAggregateVersion() + 1);
                orderRepository.save(order);

                emitOrderStateChanged(order, correlationId);

                Map<String, Object> refundPayload = Map.of(
                        "orderId", order.getId(),
                        "amount", order.getTotalAmount().longValue(),
                        "reason", "LATE_PAYMENT",
                        "cancelledAt", LocalDateTime.now().toString()
                );
                outboxPort.append(EventEnvelope.create("order.refund-required.v1", "Order", order.getId().toString(),
                        order.getAggregateVersion(), correlationId, null, order.getCustomerId(), refundPayload));

                log.info("Late payment for order {}, emitted refund-required", order.getOrderCode());
            }
        } else if ("payment.failed.v1".equals(eventType)) {
            if ("PENDING_PAYMENT".equalsIgnoreCase(order.getStatus())) {
                log.info("Payment failed for order: {}", order.getOrderCode());
            }
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
