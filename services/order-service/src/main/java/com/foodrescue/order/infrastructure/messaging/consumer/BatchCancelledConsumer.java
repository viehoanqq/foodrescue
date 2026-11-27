package com.foodrescue.order.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.application.usecase.InventoryReleaseService;
import com.foodrescue.order.application.usecase.OrderSupport;
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
public class BatchCancelledConsumer {
    private static final Logger log = LoggerFactory.getLogger(BatchCancelledConsumer.class);
    private static final String CONSUMER_NAME = "order.batch-cancelled";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final SpringDataOrderRepository orderRepository;
    private final SpringDataOrderItemRepository orderItemRepository;
    private final InventoryReleaseService inventoryReleaseService;
    private final IOutboxPort outboxPort;
    private final OrderSupport orderSupport;

    public BatchCancelledConsumer(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            SpringDataOrderRepository orderRepository,
            SpringDataOrderItemRepository orderItemRepository,
            InventoryReleaseService inventoryReleaseService,
            IOutboxPort outboxPort,
            OrderSupport orderSupport) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryReleaseService = inventoryReleaseService;
        this.outboxPort = outboxPort;
        this.orderSupport = orderSupport;
    }

    @RabbitListener(queues = RabbitConfiguration.ORDER_BATCH_CANCELLED_QUEUE)
    @Transactional
    public void onMessage(String message) throws Exception {
        JsonNode event = objectMapper.readTree(message);
        String eventIdStr = event.path("eventId").asText(null);
        String eventType = event.path("eventType").asText("");
        if (eventIdStr == null || eventIdStr.isBlank() || !"catalog.batch-cancelled.v1".equals(eventType)) {
            return;
        }

        UUID eventId = UUID.fromString(eventIdStr);

        // Inbox deduplication
        int inserted = jdbc.update(
                "INSERT IGNORE INTO inbox_events (consumer_name, event_id, processed_at) VALUES (?, ?, ?)",
                CONSUMER_NAME, eventId.toString(), Timestamp.from(Instant.now()));
        if (inserted == 0) {
            log.info("Duplicate event ignored in batch-cancelled: {}", eventId);
            return;
        }

        JsonNode payload = event.path("payload");
        long batchId = payload.path("batchId").asLong();
        String reason = payload.path("reason").asText("Batch cancelled by store");

        if (batchId <= 0) {
            return;
        }

        List<OrderItemEntity> affectedItems = orderItemRepository.findByBatchId(batchId);
        Set<Long> processedOrderIds = new HashSet<>();

        UUID correlationId = event.path("correlationId").isTextual()
                ? UUID.fromString(event.path("correlationId").asText())
                : UUID.randomUUID();

        for (OrderItemEntity item : affectedItems) {
            Long orderId = item.getOrderId();
            if (!processedOrderIds.add(orderId)) {
                continue;
            }

            OrderEntity order = orderRepository.findByIdForUpdate(orderId).orElse(null);
            if (order == null) {
                continue;
            }

            String status = order.getStatus();
            if ("PENDING_PAYMENT".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status)) {
                boolean wasPaid = "PAID".equalsIgnoreCase(order.getPaymentStatus());
                order.setStatus("CANCELLED");
                order.setCancelReason("Túi cứu trợ bị huỷ: " + reason);
                order.setCancelledAt(LocalDateTime.now());
                order.setAggregateVersion(order.getAggregateVersion() + 1);

                if (wasPaid) {
                    order.setPaymentStatus("REFUND_PENDING");
                }

                OrderEntity savedOrder = orderRepository.save(order);

                // Release inventory reservation (với retry nếu lỗi)
                inventoryReleaseService.releaseOrEnqueue(savedOrder.getReservationId(), "BATCH_CANCELLED", correlationId);

                orderSupport.emitOrderStateChanged(savedOrder, null, correlationId, null);

                if (wasPaid && orderRepository.claimRefundRequest(savedOrder.getId()) > 0) {
                    Map<String, Object> refundPayload = Map.of(
                            "orderId", savedOrder.getId(),
                            "amount", savedOrder.getTotalAmount().longValue(),
                            "reason", "BATCH_CANCELLED",
                            "cancelledAt", LocalDateTime.now().toString()
                    );
                    outboxPort.append(EventEnvelope.create(
                            "order.refund-required.v1",
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
}
