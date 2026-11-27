package com.foodrescue.order.application.usecase;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.api.dto.PageResponse;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.domain.exception.OrderException;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerOrderUseCase {
    private final SpringDataOrderRepository orderRepository;
    private final SpringDataOrderItemRepository orderItemRepository;
    private final InventoryReleaseService inventoryReleaseService;
    private final IOutboxPort outboxPort;
    private final OrderSupport orderSupport;

    public CustomerOrderUseCase(
            SpringDataOrderRepository orderRepository,
            SpringDataOrderItemRepository orderItemRepository,
            InventoryReleaseService inventoryReleaseService,
            IOutboxPort outboxPort,
            OrderSupport orderSupport) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryReleaseService = inventoryReleaseService;
        this.outboxPort = outboxPort;
        this.orderSupport = orderSupport;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(
            Long customerId, String status, LocalDate date, String code, int page, int size) {
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String cleanCode = (code != null && !code.isBlank()) ? code.trim() : null;
        LocalDateTime fromDate = (date != null) ? date.atStartOfDay() : null;
        LocalDateTime toDate = (date != null) ? date.plusDays(1).atStartOfDay() : null;

        Page<OrderEntity> result = orderRepository.searchCustomerOrders(
                customerId, cleanStatus, fromDate, toDate, cleanCode,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        return PageResponse.from(result.map(orderSupport::toResponse));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long customerId, Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> OrderException.notFound("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getCustomerId().equals(customerId)) {
            throw OrderException.forbidden("Bạn không có quyền truy cập đơn hàng này");
        }

        return orderSupport.toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long customerId, Long orderId, String reason) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> OrderException.notFound("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getCustomerId().equals(customerId)) {
            throw OrderException.forbidden("Bạn không có quyền huỷ đơn hàng này");
        }

        return executeCancel(order, customerId,
                (reason != null && !reason.isBlank()) ? reason : "Khách hàng huỷ đơn",
                "ORDER_CANCELLED");
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getAllOrdersForAdmin(
            Long storeId, String status, LocalDate date, String code, int page, int size) {
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;
        String cleanCode = (code != null && !code.isBlank()) ? code.trim() : null;
        LocalDateTime fromDate = (date != null) ? date.atStartOfDay() : null;
        LocalDateTime toDate = (date != null) ? date.plusDays(1).atStartOfDay() : null;

        Page<OrderEntity> result = orderRepository.searchAdminOrders(
                storeId, cleanStatus, fromDate, toDate, cleanCode,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        return PageResponse.from(result.map(orderSupport::toResponse));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByIdForAdmin(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> OrderException.notFound("Không tìm thấy đơn hàng #" + orderId));
        return orderSupport.toResponse(order);
    }

    @Transactional
    public OrderResponse adminCancelOrder(Long adminId, Long orderId, String reason) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> OrderException.notFound("Không tìm thấy đơn hàng #" + orderId));

        return executeCancel(order, adminId,
                (reason != null && !reason.isBlank()) ? reason : "Quản trị viên huỷ đơn",
                "ADMIN_ORDER_CANCELLED");
    }

    private OrderResponse executeCancel(OrderEntity order, Long actorId, String cancelReason, String auditAction) {
        String currentStatus = order.getStatus();
        if (!"PENDING_PAYMENT".equals(currentStatus) && !"CONFIRMED".equals(currentStatus)) {
            throw OrderException.conflict("ORDER_STATUS_CONFLICT",
                    "Đơn hàng ở trạng thái " + currentStatus + " không thể huỷ");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus("CANCELLED");
        order.setCancelReason(cancelReason);
        order.setCancelledBy(actorId);
        order.setCancelledAt(now);
        order.setAggregateVersion(order.getAggregateVersion() + 1);

        boolean wasPaid = "PAID".equals(order.getPaymentStatus());
        if (wasPaid) {
            order.setPaymentStatus("REFUND_PENDING");
        }

        OrderEntity savedOrder = orderRepository.save(order);
        UUID correlationId = UUID.randomUUID();

        // Trả tồn kho (tự động xếp hàng retry nếu lần đầu thất bại)
        inventoryReleaseService.releaseOrEnqueue(savedOrder.getReservationId(), auditAction, correlationId);

        List<OrderItemEntity> items = orderItemRepository.findByOrderId(savedOrder.getId());

        // 1. Ghi Outbox: order.state-changed.v1
        orderSupport.emitOrderStateChanged(savedOrder, items, correlationId, actorId);

        // 2. Nếu đã thu tiền, chỉ phát order.refund-required.v1 DUY NHẤT 1 LẦN
        if (wasPaid && orderRepository.claimRefundRequest(savedOrder.getId()) > 0) {
            Map<String, Object> refundPayload = Map.of(
                    "orderId", savedOrder.getId(),
                    "amount", savedOrder.getTotalAmount().longValue(),
                    "reason", savedOrder.getCancelReason(),
                    "cancelledAt", now.format(DateTimeFormatter.ISO_DATE_TIME) + "Z"
            );
            outboxPort.append(EventEnvelope.create(
                    "order.refund-required.v1",
                    "Order",
                    savedOrder.getId().toString(),
                    savedOrder.getAggregateVersion(),
                    correlationId,
                    null,
                    actorId,
                    refundPayload));
        }

        // 3. Ghi audit.action.v1 sang Identity
        Map<String, Object> auditPayload = Map.of(
                "action", auditAction,
                "entityType", "ORDER",
                "entityId", savedOrder.getId().toString(),
                "amount", savedOrder.getTotalAmount().longValue(),
                "detail", savedOrder.getCancelReason()
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
