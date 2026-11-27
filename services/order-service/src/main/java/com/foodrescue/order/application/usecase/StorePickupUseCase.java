package com.foodrescue.order.application.usecase;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.order.api.dto.OrderItemResponse;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.api.dto.PickupLookupResponse;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.domain.exception.OrderException;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorePickupUseCase {
    private final SpringDataOrderRepository orderRepository;
    private final SpringDataOrderItemRepository orderItemRepository;
    private final IOutboxPort outboxPort;

    @Value("${app.order.pickup-grace-minutes:30}")
    private int pickupGraceMinutes;

    public StorePickupUseCase(
            SpringDataOrderRepository orderRepository,
            SpringDataOrderItemRepository orderItemRepository,
            IOutboxPort outboxPort) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.outboxPort = outboxPort;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getStoreOrders(Long storeId, String status) {
        List<OrderEntity> orders = (status != null && !status.isBlank())
                ? orderRepository.findByStoreIdAndStatusOrderByCreatedAtDesc(storeId, status.trim().toUpperCase())
                : orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId);

        return orders.stream().map(this::toOrderResponse).toList();
    }

    @Transactional(readOnly = true)
    public PickupLookupResponse lookup(Long storeId, String pickupCode) {
        if (pickupCode == null || pickupCode.isBlank()) {
            throw OrderException.validation("Mã nhận hàng không được để trống");
        }

        String cleanCode = pickupCode.trim().toUpperCase();
        OrderEntity order = orderRepository.findByPickupCode(cleanCode)
                .orElseThrow(() -> OrderException.notFound("Mã nhận hàng không hợp lệ"));

        if (!order.getStoreId().equals(storeId)) {
            throw OrderException.forbidden("Đơn hàng không thuộc cửa hàng này");
        }

        if (!"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            throw OrderException.conflict("ORDER_STATUS_CONFLICT", "Đơn hàng chưa thanh toán hoặc đã hoàn tất/huỷ");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean early = now.isBefore(order.getPickupStart());
        boolean late = now.isAfter(order.getPickupEnd());

        List<OrderItemEntity> itemEntities = orderItemRepository.findByOrderId(order.getId());
        List<OrderItemResponse> itemResponses = itemEntities.stream()
                .map(i -> new OrderItemResponse(i.getId(), i.getBatchId(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();

        return new PickupLookupResponse(
                order.getId(),
                order.getOrderCode(),
                order.getPickupCode(),
                order.getCustomerId(),
                order.getItemCount(),
                order.getTotalAmount(),
                order.getPickupStart(),
                order.getPickupEnd(),
                early,
                late,
                itemResponses);
    }

    @Transactional
    public OrderResponse handover(Long storeId, Long actorId, String pickupCode) {
        if (pickupCode == null || pickupCode.isBlank()) {
            throw OrderException.validation("Mã nhận hàng không được để trống");
        }

        String cleanCode = pickupCode.trim().toUpperCase();
        OrderEntity order = orderRepository.findLockedByPickupCode(cleanCode)
                .orElseThrow(() -> OrderException.notFound("Mã nhận hàng không hợp lệ"));

        if (!order.getStoreId().equals(storeId)) {
            throw OrderException.forbidden("Đơn hàng không thuộc cửa hàng này");
        }

        if (!"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            throw OrderException.conflict("ORDER_STATUS_CONFLICT", "Đơn hàng chưa sẵn sàng giao: " + order.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(order.getPickupStart().minusMinutes(15))) {
            throw OrderException.conflict("PICKUP_TOO_EARLY", "Chưa tới khung giờ nhận hàng");
        }
        if (now.isAfter(order.getPickupEnd().plusMinutes(pickupGraceMinutes))) {
            throw OrderException.conflict("PICKUP_TOO_LATE", "Đơn hàng đã quá thời gian ân hạn nhận hàng");
        }

        order.setStatus("COMPLETED");
        order.setHandledBy(actorId);
        order.setCompletedAt(now);
        order.setAggregateVersion(order.getAggregateVersion() + 1);

        OrderEntity savedOrder = orderRepository.save(order);

        UUID correlationId = UUID.randomUUID();

        // 1. Ghi Outbox: order.state-changed.v1
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(savedOrder.getId());
        Map<String, Object> statePayload = new LinkedHashMap<>();
        statePayload.put("orderId", savedOrder.getId());
        statePayload.put("storeId", savedOrder.getStoreId());
        statePayload.put("customerId", savedOrder.getCustomerId());
        statePayload.put("status", savedOrder.getStatus());
        statePayload.put("paymentStatus", savedOrder.getPaymentStatus());
        statePayload.put("totalAmount", savedOrder.getTotalAmount().longValue());
        statePayload.put("commissionAmount", savedOrder.getCommissionAmount().longValue());
        statePayload.put("storeEarning", savedOrder.getStoreEarning().longValue());
        statePayload.put("completedAt", savedOrder.getCompletedAt().toString());
        statePayload.put("reservationId", savedOrder.getReservationId() != null ? savedOrder.getReservationId().toString() : null);
        statePayload.put("items", items.stream()
                .map(it -> Map.of("batchId", it.getBatchId(), "quantity", it.getQuantity()))
                .toList());

        outboxPort.append(EventEnvelope.create(
                MessagingNames.ORDER_STATE_CHANGED_EVENT,
                "Order",
                savedOrder.getId().toString(),
                savedOrder.getAggregateVersion(),
                correlationId,
                null,
                actorId,
                statePayload));

        // 2. Ghi audit.action.v1 sang Identity
        Map<String, Object> auditPayload = Map.of(
                "action", "ORDER_COMPLETED",
                "entityType", "ORDER",
                "entityId", savedOrder.getId().toString(),
                "amount", savedOrder.getTotalAmount().longValue(),
                "detail", "Giao hàng mã " + savedOrder.getPickupCode()
        );
        outboxPort.append(EventEnvelope.create(
                MessagingNames.AUDIT_ACTION_EVENT,
                "Order",
                savedOrder.getId().toString(),
                savedOrder.getAggregateVersion(),
                correlationId,
                null,
                actorId,
                auditPayload));

        return toOrderResponse(savedOrder);
    }

    public OrderResponse toOrderResponse(OrderEntity o) {
        List<OrderItemEntity> itemEntities = orderItemRepository.findByOrderId(o.getId());
        List<OrderItemResponse> itemResponses = itemEntities.stream()
                .map(i -> new OrderItemResponse(i.getId(), i.getBatchId(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();

        return new OrderResponse(
                o.getId(),
                o.getOrderCode(),
                o.getCustomerId(),
                o.getStoreId(),
                o.getItemCount(),
                o.getTotalAmount(),
                o.getCommissionRate(),
                o.getCommissionAmount(),
                o.getStoreEarning(),
                o.getPickupStart(),
                o.getPickupEnd(),
                "PAID".equals(o.getPaymentStatus()) ? o.getPickupCode() : null,
                o.getStatus(),
                o.getPaymentStatus(),
                o.getCancelReason(),
                o.getPaymentExpiresAt(),
                o.getCompletedAt(),
                o.getCreatedAt(),
                o.getReservationId(),
                o.getAggregateVersion(),
                itemResponses);
    }
}
