package com.foodrescue.order.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IInboxPort;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.domain.model.enums.OrderStatus;
import com.foodrescue.order.domain.model.enums.PaymentStatus;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentResultsConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentResultsConsumer.class);
    private static final String CONSUMER_NAME = MessagingNames.ORDER_PAYMENT_RESULTS_QUEUE;

    private final IInboxPort inbox;
    private final ObjectMapper json;
    private final SpringDataOrderRepository orderRepository;
    private final SpringDataOrderItemRepository orderItemRepository;
    private final IInventoryPort inventoryPort;
    private final IOutboxPort outboxPort;

    public PaymentResultsConsumer(
            IInboxPort inbox,
            ObjectMapper json,
            SpringDataOrderRepository orderRepository,
            SpringDataOrderItemRepository orderItemRepository,
            IInventoryPort inventoryPort,
            IOutboxPort outboxPort) {
        this.inbox = inbox;
        this.json = json;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryPort = inventoryPort;
        this.outboxPort = outboxPort;
    }

    @RabbitListener(queues = MessagingNames.ORDER_PAYMENT_RESULTS_QUEUE)
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
        if (!inbox.claim(CONSUMER_NAME, eventId)) {
            log.info("Duplicate event ignored: {}", eventId);
            return;
        }

        JsonNode payload = event.path("payload");
        long orderId = payload.path("orderId").asLong();
        if (orderId <= 0) {
            return;
        }

        OrderEntity order = orderRepository.findLockedById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order not found for payment result: {}", orderId);
            return;
        }

        UUID correlationId = event.path("correlationId").isTextual()
                ? UUID.fromString(event.path("correlationId").asText())
                : UUID.randomUUID();

        if (MessagingNames.PAYMENT_SUCCEEDED_EVENT.equals(eventType)) {
            OrderStatus status = order.getStatus();
            if (status == OrderStatus.PENDING_PAYMENT) {
                order.setStatus(OrderStatus.CONFIRMED);
                order.setPaymentStatus(PaymentStatus.PAID);
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
                outboxPort.append(EventEnvelope.create(MessagingNames.AUDIT_ACTION_EVENT, "Order", order.getId().toString(),
                        order.getAggregateVersion(), correlationId, null, order.getCustomerId(), auditPayload));

                log.info("Order {} confirmed by payment success", order.getOrderCode());
            } else if (status != null && status.isLatePaymentRefundable()
                    && (order.getPaymentStatus() == null || !order.getPaymentStatus().isRefundInitiated())) {
                // Payment received after cancel/expired -> trigger refund required
                order.setPaymentStatus(PaymentStatus.REFUND_PENDING);
                order.setAggregateVersion(order.getAggregateVersion() + 1);
                orderRepository.save(order);

                emitOrderStateChanged(order, correlationId);

                Map<String, Object> refundPayload = Map.of(
                        "orderId", order.getId(),
                        "amount", order.getTotalAmount().longValue(),
                        "reason", "LATE_PAYMENT",
                        "cancelledAt", LocalDateTime.now().toString()
                );
                outboxPort.append(EventEnvelope.create(MessagingNames.ORDER_REFUND_REQUIRED_EVENT, "Order", order.getId().toString(),
                        order.getAggregateVersion(), correlationId, null, order.getCustomerId(), refundPayload));

                log.info("Late payment for order {}, emitted refund-required", order.getOrderCode());
            }
        } else if (MessagingNames.PAYMENT_FAILED_EVENT.equals(eventType)) {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
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
        statePayload.put("status", order.getStatus().name());
        statePayload.put("paymentStatus", order.getPaymentStatus().name());
        statePayload.put("totalAmount", order.getTotalAmount().longValue());
        statePayload.put("commissionAmount", order.getCommissionAmount().longValue());
        statePayload.put("storeEarning", order.getStoreEarning().longValue());
        statePayload.put("completedAt", order.getCompletedAt() != null ? order.getCompletedAt().toString() : null);
        statePayload.put("reservationId", order.getReservationId() != null ? order.getReservationId().toString() : null);
        statePayload.put("items", items.stream()
                .map(it -> Map.of("batchId", it.getBatchId(), "quantity", it.getQuantity()))
                .toList());

        outboxPort.append(EventEnvelope.create(
                MessagingNames.ORDER_STATE_CHANGED_EVENT,
                "Order",
                order.getId().toString(),
                order.getAggregateVersion(),
                correlationId,
                null,
                null,
                statePayload));
    }
}
