package com.foodrescue.order.application.usecase;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.domain.model.enums.OrderStatus;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSchedulerUseCase {
    private static final Logger log = LoggerFactory.getLogger(OrderSchedulerUseCase.class);

    private final SpringDataOrderRepository orderRepository;
    private final SpringDataOrderItemRepository orderItemRepository;
    private final IInventoryPort inventoryPort;
    private final IOutboxPort outboxPort;

    public OrderSchedulerUseCase(
            SpringDataOrderRepository orderRepository,
            SpringDataOrderItemRepository orderItemRepository,
            IInventoryPort inventoryPort,
            IOutboxPort outboxPort) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryPort = inventoryPort;
        this.outboxPort = outboxPort;
    }

    @Scheduled(fixedDelay = 60000) // Mỗi 1 phút
    @Transactional
    public void expireUnpaidOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> expiredOrderIds = orderRepository.findExpiredPendingOrderIds(OrderStatus.PENDING_PAYMENT, now);
        for (Long id : expiredOrderIds) {
            OrderEntity order = orderRepository.findLockedById(id).orElse(null);
            if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT
                    || !order.getPaymentExpiresAt().isBefore(LocalDateTime.now())) continue;
            order.setStatus(OrderStatus.EXPIRED);
            order.setCancelReason("Hết hạn thanh toán (quá 15 phút)");
            order.setAggregateVersion(order.getAggregateVersion() + 1);
            orderRepository.save(order);

            if (order.getReservationId() != null) {
                try {
                    inventoryPort.release(order.getReservationId(), UUID.randomUUID());
                } catch (Exception ex) {
                    log.warn("Failed to release reservation on expire orderId={}: {}", order.getId(), ex.getMessage());
                }
            }

            emitStateChangedEvent(order);
            log.info("Expired unpaid order: {}", order.getOrderCode());
        }
    }

    @Scheduled(fixedDelay = 300000) // Mỗi 5 phút
    @Transactional
    public void markNoShowOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(30);
        List<Long> noShowOrderIds = orderRepository.findNoShowOrderIds(OrderStatus.CONFIRMED, deadline);
        for (Long id : noShowOrderIds) {
            OrderEntity order = orderRepository.findLockedById(id).orElse(null);
            if (order == null || order.getStatus() != OrderStatus.CONFIRMED
                    || !order.getPickupEnd().isBefore(deadline)) continue;
            order.setStatus(OrderStatus.NO_SHOW);
            order.setCompletedAt(LocalDateTime.now());
            order.setCancelReason("Khách hàng không đến nhận trong khung giờ");
            order.setAggregateVersion(order.getAggregateVersion() + 1);
            orderRepository.save(order);

            emitStateChangedEvent(order);
            log.info("Marked order as NO_SHOW: {}", order.getOrderCode());
        }
    }

    private void emitStateChangedEvent(OrderEntity order) {
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
                UUID.randomUUID(),
                null,
                null,
                statePayload));
    }
}
