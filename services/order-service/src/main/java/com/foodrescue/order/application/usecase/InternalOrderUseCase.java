package com.foodrescue.order.application.usecase;

import com.foodrescue.order.api.dto.PaymentIntentResponse;
import com.foodrescue.order.api.dto.ReviewEligibilityResponse;
import com.foodrescue.order.domain.exception.OrderException;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalOrderUseCase {
    private final SpringDataOrderRepository orderRepository;

    public InternalOrderUseCase(SpringDataOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public PaymentIntentResponse getPaymentIntent(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> OrderException.notFound("Không tìm thấy đơn hàng #" + orderId));

        return new PaymentIntentResponse(
                order.getId(),
                order.getOrderCode(),
                order.getCustomerId(),
                order.getStoreId(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getPaymentStatus().name(),
                order.getPaymentExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public ReviewEligibilityResponse getReviewEligibility(Long orderId, Long customerId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> OrderException.notFound("Không tìm thấy đơn hàng #" + orderId));

        boolean matchCustomer = customerId == null || order.getCustomerId().equals(customerId);
        boolean isCompleted = order.getStatus() != null && order.getStatus().isCompleted();

        return new ReviewEligibilityResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStoreId(),
                matchCustomer && isCompleted,
                order.getStatus() != null ? order.getStatus().name() : null
        );
    }
}
