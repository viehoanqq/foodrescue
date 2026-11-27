package com.foodrescue.order.application.usecase;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.order.api.dto.OrderItemResponse;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.api.dto.PageResponse;
import com.foodrescue.order.api.dto.PickupLookupResponse;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.domain.exception.OrderException;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorePickupUseCase {
    private final SpringDataOrderRepository orderRepository;
    private final SpringDataOrderItemRepository orderItemRepository;
    private final IOutboxPort outboxPort;
    private final OrderSupport orderSupport;

    @Value("${app.order.pickup-grace-minutes:30}")
    private int pickupGraceMinutes;

    public StorePickupUseCase(
            SpringDataOrderRepository orderRepository,
            SpringDataOrderItemRepository orderItemRepository,
            IOutboxPort outboxPort,
            OrderSupport orderSupport) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.outboxPort = outboxPort;
        this.orderSupport = orderSupport;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getStoreOrders(
            Long storeId, String status, LocalDate date, String code, int page, int size) {
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String cleanCode = (code != null && !code.isBlank()) ? code.trim() : null;
        LocalDateTime fromDate = (date != null) ? date.atStartOfDay() : null;
        LocalDateTime toDate = (date != null) ? date.plusDays(1).atStartOfDay() : null;

        Page<OrderEntity> result = orderRepository.searchStoreOrders(
                storeId, cleanStatus, fromDate, toDate, cleanCode,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        return PageResponse.from(result.map(orderSupport::toResponse));
    }

    @Transactional(readOnly = true)
    public OrderResponse getStoreOrderDetail(Long storeId, Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> OrderException.notFound("Không tìm thấy đơn hàng #" + orderId));
        if (!order.getStoreId().equals(storeId)) {
            throw OrderException.forbidden("Đơn hàng không thuộc cửa hàng này");
        }
        return orderSupport.toResponse(order);
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

        if (!"CONFIRMED".equalsIgnoreCase(order.getStatus()) || !"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw OrderException.conflict("ORDER_STATUS_CONFLICT", "Đơn hàng chưa thanh toán hoặc đã hoàn tất/huỷ");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean early = now.isBefore(order.getPickupStart().minusMinutes(15));
        boolean late = now.isAfter(order.getPickupEnd());
        boolean canHandover = !early && !now.isAfter(order.getPickupEnd().plusMinutes(pickupGraceMinutes));

        List<OrderItemEntity> itemEntities = orderItemRepository.findByOrderId(order.getId());
        List<OrderItemResponse> itemResponses = itemEntities.stream()
                .map(i -> new OrderItemResponse(i.getId(), i.getBatchId(), i.getQuantity(), i.getUnitPrice(), i.getSubtotal()))
                .toList();

        return new PickupLookupResponse(
                order.getId(),
                order.getOrderCode(),
                order.getPickupCode(),
                order.getCustomerId(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getItemCount(),
                order.getTotalAmount(),
                order.getPickupStart(),
                order.getPickupEnd(),
                early,
                late,
                canHandover,
                itemResponses);
    }

    @Transactional
    public OrderResponse handover(Long storeId, Long actorId, String pickupCode) {
        if (pickupCode == null || pickupCode.isBlank()) {
            throw OrderException.validation("Mã nhận hàng không được để trống");
        }

        String cleanCode = pickupCode.trim().toUpperCase();
        OrderEntity order = orderRepository.findByPickupCodeForUpdate(cleanCode)
                .orElseThrow(() -> OrderException.notFound("Mã nhận hàng không hợp lệ"));

        if (!order.getStoreId().equals(storeId)) {
            throw OrderException.forbidden("Đơn hàng không thuộc cửa hàng này");
        }

        if (!"CONFIRMED".equalsIgnoreCase(order.getStatus()) || !"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
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
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(savedOrder.getId());

        // 1. Ghi Outbox: order.state-changed.v1
        orderSupport.emitOrderStateChanged(savedOrder, items, correlationId, actorId);

        // 2. Ghi audit.action.v1 sang Identity
        Map<String, Object> auditPayload = Map.of(
                "action", "ORDER_COMPLETED",
                "entityType", "ORDER",
                "entityId", savedOrder.getId().toString(),
                "amount", savedOrder.getTotalAmount().longValue(),
                "detail", "Giao hàng mã " + savedOrder.getPickupCode()
        );
        outboxPort.append(EventEnvelope.create(
                "audit.action.v1",
                "Order",
                savedOrder.getId().toString(),
                savedOrder.getAggregateVersion(),
                correlationId,
                null,
                actorId,
                auditPayload));

        return orderSupport.toResponse(savedOrder, items);
    }
}
