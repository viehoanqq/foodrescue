package com.foodrescue.order.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.application.usecase.InventoryReleaseService;
import com.foodrescue.order.application.usecase.OrderSupport;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.messaging.RabbitConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
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
    private final ObjectMapper objectMapper;
    private final SpringDataOrderRepository orderRepository;
    private final IOutboxPort outboxPort;
    private final InventoryReleaseService inventoryReleaseService;
    private final OrderSupport orderSupport;

    public PaymentResultsConsumer(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            SpringDataOrderRepository orderRepository,
            IOutboxPort outboxPort,
            InventoryReleaseService inventoryReleaseService,
            OrderSupport orderSupport) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
        this.outboxPort = outboxPort;
        this.inventoryReleaseService = inventoryReleaseService;
        this.orderSupport = orderSupport;
    }

    @RabbitListener(queues = RabbitConfiguration.ORDER_PAYMENT_RESULTS_QUEUE)
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
            log.info("Duplicate event ignored: {}", eventId);
            return;
        }

        JsonNode payload = event.path("payload");
        long orderId = payload.path("orderId").asLong();
        if (orderId <= 0) {
            return;
        }

        OrderEntity order = orderRepository.findByIdForUpdate(orderId).orElse(null);
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

                orderSupport.emitOrderStateChanged(order, null, correlationId, null);

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
                // Thanh toán về muộn sau khi đã huỷ/hết hạn -> chỉ phát order.refund-required.v1 nếu chưa từng yêu cầu hoàn tiền
                if (orderRepository.claimRefundRequest(order.getId()) > 0) {
                    order.setPaymentStatus("REFUND_PENDING");
                    order.setAggregateVersion(order.getAggregateVersion() + 1);
                    orderRepository.save(order);

                    orderSupport.emitOrderStateChanged(order, null, correlationId, null);

                    Map<String, Object> refundPayload = Map.of(
                            "orderId", order.getId(),
                            "amount", order.getTotalAmount().longValue(),
                            "reason", "LATE_PAYMENT",
                            "cancelledAt", LocalDateTime.now().toString()
                    );
                    outboxPort.append(EventEnvelope.create("order.refund-required.v1", "Order", order.getId().toString(),
                            order.getAggregateVersion(), correlationId, null, order.getCustomerId(), refundPayload));

                    log.info("Late payment for order {}, emitted refund-required once", order.getOrderCode());
                } else {
                    log.info("Refund already requested for order {}, skipping duplicate refund-required", order.getOrderCode());
                }
            }
        } else if ("payment.failed.v1".equals(eventType)) {
            if ("PENDING_PAYMENT".equalsIgnoreCase(order.getStatus())) {
                order.setStatus("CANCELLED");
                order.setCancelReason("Thanh toán thất bại");
                order.setCancelledAt(LocalDateTime.now());
                order.setAggregateVersion(order.getAggregateVersion() + 1);
                orderRepository.save(order);

                inventoryReleaseService.releaseOrEnqueue(order.getReservationId(), "PAYMENT_FAILED", correlationId);
                orderSupport.emitOrderStateChanged(order, null, correlationId, null);
                log.info("Order {} cancelled and inventory released due to payment.failed.v1", order.getOrderCode());
            }
        }
    }
}
