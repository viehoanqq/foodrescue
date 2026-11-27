package com.foodrescue.order.application.usecase;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.order.api.dto.OrderItemResponse;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderSupport {
    private final SpringDataOrderItemRepository orderItemRepository;
    private final IOutboxPort outboxPort;

    public OrderSupport(SpringDataOrderItemRepository orderItemRepository, IOutboxPort outboxPort) {
        this.orderItemRepository = orderItemRepository;
        this.outboxPort = outboxPort;
    }

    public OrderResponse toResponse(OrderEntity o) {
        List<OrderItemEntity> itemEntities = orderItemRepository.findByOrderId(o.getId());
        return toResponse(o, itemEntities);
    }

    public OrderResponse toResponse(OrderEntity o, List<OrderItemEntity> itemEntities) {
        List<OrderItemResponse> itemResponses = itemEntities.stream()
                .map(i -> new OrderItemResponse(i.getId(), i.getBatchId(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();

        // Mã nhận hàng chỉ mở sau khi thanh toán thành công (PAID) và đơn ở trạng thái CONFIRMED hoặc COMPLETED
        boolean canViewPickupCode = "PAID".equalsIgnoreCase(o.getPaymentStatus())
                && ("CONFIRMED".equalsIgnoreCase(o.getStatus()) || "COMPLETED".equalsIgnoreCase(o.getStatus()));
        String visiblePickupCode = canViewPickupCode ? o.getPickupCode() : null;

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
                visiblePickupCode,
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

    public void emitOrderStateChanged(OrderEntity order, List<OrderItemEntity> items, UUID correlationId, Long actorId) {
        List<OrderItemEntity> resolvedItems = (items != null) ? items : orderItemRepository.findByOrderId(order.getId());
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
        statePayload.put("items", resolvedItems.stream()
                .map(it -> Map.of("batchId", it.getBatchId(), "quantity", it.getQuantity()))
                .toList());

        outboxPort.append(EventEnvelope.create(
                "order.state-changed.v1",
                "Order",
                order.getId().toString(),
                order.getAggregateVersion(),
                correlationId != null ? correlationId : UUID.randomUUID(),
                null,
                actorId,
                statePayload));
    }
}
