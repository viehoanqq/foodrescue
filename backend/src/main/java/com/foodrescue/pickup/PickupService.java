package com.foodrescue.pickup;

import com.foodrescue.common.entity.Order;
import com.foodrescue.common.entity.User;
import com.foodrescue.common.enums.AuditAction;
import com.foodrescue.common.enums.EntityType;
import com.foodrescue.common.enums.OrderStatus;
import com.foodrescue.common.exception.BusinessException;
import com.foodrescue.common.exception.ErrorCode;
import com.foodrescue.common.repository.OrderRepository;
import com.foodrescue.common.repository.UserRepository;
import com.foodrescue.common.service.AuditService;
import com.foodrescue.order.OrderItemResponse;
import com.foodrescue.order.OrderResponse;
import com.foodrescue.order.OrderService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PickupService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final AuditService auditService;

    @Value("${app.order.pickup-grace-minutes:30}")
    private int pickupGraceMinutes;

    public PickupService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            OrderService orderService,
            AuditService auditService) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.orderService = orderService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PickupLookupResponse lookupByPickupCode(Long storeId, String pickupCode) {
        String cleanCode = pickupCode != null ? pickupCode.trim().toUpperCase() : "";
        Order order = orderRepository.findByPickupCode(cleanCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.PICKUP_CODE_INVALID));

        if (!order.getStore().getId().equals(storeId)) {
            throw new BusinessException(ErrorCode.PICKUP_CODE_INVALID);
        }

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.DATA_CONSTRAINT_VIOLATION);
        }

        LocalDateTime now = LocalDateTime.now();
        boolean early = now.isBefore(order.getPickupStart());
        boolean late = now.isAfter(order.getPickupEnd());

        OrderResponse res = orderService.toOrderResponse(order);
        List<OrderItemResponse> items = res.items();

        return new PickupLookupResponse(
                order.getId(),
                order.getOrderCode(),
                order.getPickupCode(),
                order.getCustomer().getFullName(),
                order.getCustomer().getPhone(),
                order.getItemCount(),
                order.getTotalAmount(),
                order.getPickupStart(),
                order.getPickupEnd(),
                items,
                early,
                late);
    }

    @Transactional
    public OrderResponse handoverByPickupCode(Long actorId, Long storeId, String pickupCode) {
        String cleanCode = pickupCode != null ? pickupCode.trim().toUpperCase() : "";
        Order order = orderRepository.findByPickupCode(cleanCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.PICKUP_CODE_INVALID));

        if (!order.getStore().getId().equals(storeId)) {
            throw new BusinessException(ErrorCode.PICKUP_CODE_INVALID);
        }

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.DATA_CONSTRAINT_VIOLATION);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(order.getPickupStart())) {
            throw new BusinessException(ErrorCode.PICKUP_TOO_EARLY);
        }

        if (now.isAfter(order.getPickupEnd().plusMinutes(pickupGraceMinutes))) {
            throw new BusinessException(ErrorCode.PICKUP_TOO_LATE);
        }

        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        order.setStatus(OrderStatus.COMPLETED);
        order.setHandledBy(actor);
        order.setCompletedAt(now);
        orderRepository.save(order);

        auditService.log(
                actorId,
                AuditAction.ORDER_COMPLETED,
                EntityType.ORDER,
                order.getId(),
                order.getTotalAmount(),
                "Giao hàng mã " + order.getPickupCode());

        return orderService.toOrderResponse(order);
    }
}
