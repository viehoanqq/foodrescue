package com.foodrescue.order.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.application.port.out.IInboxPort;
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
public class BatchCancelledConsumer {
    private static final Logger log = LoggerFactory.getLogger(BatchCancelledConsumer.class);
    private static final String CONSUMER_NAME = MessagingNames.ORDER_BATCH_CANCELLED_QUEUE;

    private final IInboxPort inbox;
    private final ObjectMapper json;
    private final SpringDataOrderRepository orderRepository;
    private final SpringDataOrderItemRepository orderItemRepository;
    private final IInventoryPort inventoryPort;
    private final IOutboxPort outboxPort;

    public BatchCancelledConsumer(
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

    @RabbitListener(queues = MessagingNames.ORDER_BATCH_CANCELLED_QUEUE)
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
        if (!inbox.claim(CONSUMER_NAME, eventId)) {
            log.info("Duplicate event ignored in batch-cancelled: {}", eventId);
            return;
        }

        JsonNode payload = event.path("payload");
        long batchId = payload.path("batchId").asLong();
        String reason = payload.path("reason").asText("Túi cứu trợ bị huỷ");

        if (batchId <= 0) {
            return;
        }

        List<OrderItemEntity> affectedItems = orderItemRepository.findByBatchId(batchId);
        Set<Long> processedOrderIds = new TreeSet<>();
        for (OrderItemEntity item : affectedItems) processedOrderIds.add(item.getOrderId());

        UUID correlationId = event.path("correlationId").isTextual()
                ? UUID.fromString(event.path("correlationId").asText())
                : UUID.randomUUID();

        for (Long orderId : processedOrderIds) {
            OrderEntity order = orderRepository.findLockedById(orderId).orElse(null);
            if (order == null) {
                continue;
            }

            OrderStatus status = order.getStatus();
            if (status != null && status.isActiveForBatchCancel()) {
                boolean wasPaid = order.getPaymentStatus() != null && order.getPaymentStatus().isPaid();
                order.setStatus(OrderStatus.CANCELLED);
                order.setCancelReason("Túi cứu trợ bị huỷ: " + reason);
                order.setCancelledAt(LocalDateTime.now());
                order.setAggregateVersion(order.getAggregateVersion() + 1);

                if (wasPaid) {
                    order.setPaymentStatus(PaymentStatus.REFUND_PENDING);
                }

                OrderEntity savedOrder = orderRepository.save(order);

                // Release inventory reservation
                if (savedOrder.getReservationId() != null) {
                    try {
                        inventoryPort.release(savedOrder.getReservationId(), correlationId);
                    } catch (Exception ex) {
                        log.warn("Failed to release reservation on batch-cancelled orderId={}: {}", orderId, ex.getMessage());
                    }
                }

                emitOrderStateChanged(savedOrder, correlationId);

                if (wasPaid) {
                    Map<String, Object> refundPayload = Map.of(
                            "orderId", savedOrder.getId(),
                            "amount", savedOrder.getTotalAmount().longValue(),
                            "reason", "BATCH_CANCELLED",
                            "cancelledAt", LocalDateTime.now().toString()
                    );
                    outboxPort.append(EventEnvelope.create(
                            MessagingNames.ORDER_REFUND_REQUIRED_EVENT,
                            "Order",
                            savedOrder.getId().toString(),
                            savedOrder.getAggregateVersion(),
                            correlationId,
                            null,
                            null,
                            refundPayload));
                }

                log.info("Cancelled order {} due to batch {} cancellation", savedOrder.getOrderCode(), batchId);
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
