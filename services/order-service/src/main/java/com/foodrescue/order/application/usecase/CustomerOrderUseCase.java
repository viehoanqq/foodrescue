package com.foodrescue.order.application.usecase;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.order.api.dto.OrderItemResponse;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.api.dto.OrderPageResponse;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.domain.exception.OrderException;
import com.foodrescue.order.domain.model.enums.OrderStatus;
import com.foodrescue.order.domain.model.enums.PaymentStatus;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerOrderUseCase {
    private static final Logger log = LoggerFactory.getLogger(CustomerOrderUseCase.class);

    private final SpringDataOrderRepository orderRepository;
    private final SpringDataOrderItemRepository orderItemRepository;
    private final IInventoryPort inventoryPort;
    private final IOutboxPort outboxPort;

    public CustomerOrderUseCase(
            SpringDataOrderRepository orderRepository,
            SpringDataOrderItemRepository orderItemRepository,
            IInventoryPort inventoryPort,
            IOutboxPort outboxPort) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryPort = inventoryPort;
        this.outboxPort = outboxPort;
    }

    @Transactional(readOnly = true)
    public OrderPageResponse getMyOrders(Long customerId, String status, int page, int size) {
        return OrderPageResponse.from(orderRepository.findCustomerOrders(customerId, parseStatusFilter(status), paging(page, size))
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long customerId, Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> OrderException.notFound("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getCustomerId().equals(customerId)) {
            throw OrderException.forbidden("Bạn không có quyền truy cập đơn hàng này");
        }

        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long customerId, Long orderId, String reason) {
        OrderEntity order = orderRepository.findLockedById(orderId)
                .orElseThrow(() -> OrderException.notFound("Không tìm thấy đơn hàng #" + orderId));

        if (!order.getCustomerId().equals(customerId)) {
            throw OrderException.forbidden("Bạn không có quyền huỷ đơn hàng này");
        }

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == null || !currentStatus.canCancelByCustomer()) {
            throw OrderException.conflict("ORDER_STATUS_CONFLICT", "Đơn hàng ở trạng thái " + currentStatus + " không thể huỷ");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason != null && !reason.isBlank() ? reason : "Khách hàng huỷ đơn");
        order.setCancelledBy(customerId);
        order.setCancelledAt(now);
        order.setAggregateVersion(order.getAggregateVersion() + 1);

        boolean wasPaid = order.getPaymentStatus() != null && order.getPaymentStatus().isPaid();
        if (wasPaid) {
            order.setPaymentStatus(PaymentStatus.REFUND_PENDING);
        }

        OrderEntity savedOrder = orderRepository.save(order);

        // Trả tồn kho
        if (savedOrder.getReservationId() != null) {
            try {
                inventoryPort.release(savedOrder.getReservationId(), UUID.randomUUID());
            } catch (Exception ex) {
                log.warn("Failed to release reservation on cancel orderId={}: {}", orderId, ex.getMessage());
            }
        }

        UUID correlationId = UUID.randomUUID();

        // 1. Ghi Outbox: order.state-changed.v1
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(savedOrder.getId());
        Map<String, Object> statePayload = new LinkedHashMap<>();
        statePayload.put("orderId", savedOrder.getId());
        statePayload.put("storeId", savedOrder.getStoreId());
        statePayload.put("customerId", savedOrder.getCustomerId());
        statePayload.put("status", savedOrder.getStatus().name());
        statePayload.put("paymentStatus", savedOrder.getPaymentStatus().name());
        statePayload.put("totalAmount", savedOrder.getTotalAmount().longValue());
        statePayload.put("commissionAmount", savedOrder.getCommissionAmount().longValue());
        statePayload.put("storeEarning", savedOrder.getStoreEarning().longValue());
        statePayload.put("completedAt", null);
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
                customerId,
                statePayload));

        // 2. Nếu đã thu tiền, gửi order.refund-required.v1 sang Finance
        if (wasPaid) {
            Map<String, Object> refundPayload = Map.of(
                    "orderId", savedOrder.getId(),
                    "amount", savedOrder.getTotalAmount().longValue(),
                    "reason", savedOrder.getCancelReason(),
                    "cancelledAt", now.format(DateTimeFormatter.ISO_DATE_TIME) + "Z"
            );
            outboxPort.append(EventEnvelope.create(
                    MessagingNames.ORDER_REFUND_REQUIRED_EVENT,
                    "Order",
                    savedOrder.getId().toString(),
                    savedOrder.getAggregateVersion(),
                    correlationId,
                    null,
                    customerId,
                    refundPayload));
        }

        // 3. Ghi audit.action.v1 sang Identity
        Map<String, Object> auditPayload = Map.of(
                "action", "ORDER_CANCELLED",
                "entityType", "ORDER",
                "entityId", savedOrder.getId().toString(),
                "amount", savedOrder.getTotalAmount().longValue(),
                "detail", savedOrder.getCancelReason()
        );
        outboxPort.append(EventEnvelope.create(
                MessagingNames.AUDIT_ACTION_EVENT,
                "Order",
                savedOrder.getId().toString(),
                savedOrder.getAggregateVersion(),
                correlationId,
                null,
                customerId,
                auditPayload));

        return toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderPageResponse getAllOrdersForAdmin(String code, String status, int page, int size) {
        return OrderPageResponse.from(orderRepository.findAdminOrders(parseStatusFilter(status), normalize(code), paging(page, size))
                .map(this::toResponse));
    }

    public static OrderStatus parseStatusFilter(String raw) {
        try {
            return OrderStatus.fromNullable(raw).orElse(null);
        } catch (IllegalArgumentException ex) {
            throw OrderException.validation("Trạng thái đơn hàng không hợp lệ: " + raw);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public static PageRequest paging(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw OrderException.validation("page phải >= 0 và size phải từ 1 đến 100");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByIdForAdmin(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> OrderException.notFound("Không tìm thấy đơn hàng #" + orderId));
        return toResponse(order);
    }

    @Transactional
    public OrderResponse adminCancelOrder(Long adminId, Long orderId, String reason) {
        OrderEntity order = orderRepository.findLockedById(orderId)
                .orElseThrow(() -> OrderException.notFound("Không tìm thấy đơn hàng #" + orderId));

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus != null && currentStatus.isTerminalForAdmin()) {
            throw OrderException.conflict("ORDER_STATUS_CONFLICT", "Đơn hàng ở trạng thái " + currentStatus + " không thể huỷ");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason != null && !reason.isBlank() ? reason : "Quản trị viên huỷ đơn");
        order.setCancelledBy(adminId);
        order.setCancelledAt(now);
        order.setAggregateVersion(order.getAggregateVersion() + 1);

        boolean wasPaid = order.getPaymentStatus() != null && order.getPaymentStatus().isPaid();
        if (wasPaid) {
            order.setPaymentStatus(PaymentStatus.REFUND_PENDING);
        }

        OrderEntity savedOrder = orderRepository.save(order);

        // Trả tồn kho
        if (savedOrder.getReservationId() != null) {
            try {
                inventoryPort.release(savedOrder.getReservationId(), UUID.randomUUID());
            } catch (Exception ex) {
                log.warn("Failed to release reservation on admin cancel orderId={}: {}", orderId, ex.getMessage());
            }
        }

        UUID correlationId = UUID.randomUUID();

        // 1. Ghi Outbox: order.state-changed.v1
        List<OrderItemEntity> items = orderItemRepository.findByOrderId(savedOrder.getId());
        Map<String, Object> statePayload = new LinkedHashMap<>();
        statePayload.put("orderId", savedOrder.getId());
        statePayload.put("storeId", savedOrder.getStoreId());
        statePayload.put("customerId", savedOrder.getCustomerId());
        statePayload.put("status", savedOrder.getStatus().name());
        statePayload.put("paymentStatus", savedOrder.getPaymentStatus().name());
        statePayload.put("totalAmount", savedOrder.getTotalAmount().longValue());
        statePayload.put("commissionAmount", savedOrder.getCommissionAmount().longValue());
        statePayload.put("storeEarning", savedOrder.getStoreEarning().longValue());
        statePayload.put("completedAt", null);
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
                adminId,
                statePayload));

        // 2. Nếu đã thu tiền, gửi order.refund-required.v1 sang Finance
        if (wasPaid) {
            Map<String, Object> refundPayload = Map.of(
                    "orderId", savedOrder.getId(),
                    "amount", savedOrder.getTotalAmount().longValue(),
                    "reason", savedOrder.getCancelReason(),
                    "cancelledAt", now.format(DateTimeFormatter.ISO_DATE_TIME) + "Z"
            );
            outboxPort.append(EventEnvelope.create(
                    MessagingNames.ORDER_REFUND_REQUIRED_EVENT,
                    "Order",
                    savedOrder.getId().toString(),
                    savedOrder.getAggregateVersion(),
                    correlationId,
                    null,
                    adminId,
                    refundPayload));
        }

        // 3. Ghi audit.action.v1 sang Identity
        Map<String, Object> auditPayload = Map.of(
                "action", "ADMIN_ORDER_CANCELLED",
                "entityType", "ORDER",
                "entityId", savedOrder.getId().toString(),
                "amount", savedOrder.getTotalAmount().longValue(),
                "detail", savedOrder.getCancelReason()
        );
        outboxPort.append(EventEnvelope.create(
                MessagingNames.AUDIT_ACTION_EVENT,
                "Order",
                savedOrder.getId().toString(),
                savedOrder.getAggregateVersion(),
                correlationId,
                null,
                adminId,
                auditPayload));

        return toResponse(savedOrder);
    }

    public OrderResponse toResponse(OrderEntity o) {
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
                o.getPaymentStatus() != null && o.getPaymentStatus().isPaid() ? o.getPickupCode() : null,
                o.getStatus() != null ? o.getStatus().name() : null,
                o.getPaymentStatus() != null ? o.getPaymentStatus().name() : null,
                o.getCancelReason(),
                o.getPaymentExpiresAt(),
                o.getCompletedAt(),
                o.getCreatedAt(),
                o.getReservationId(),
                o.getAggregateVersion(),
                itemResponses);
    }
}
