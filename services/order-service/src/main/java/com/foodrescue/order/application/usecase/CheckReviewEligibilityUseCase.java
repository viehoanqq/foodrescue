package com.foodrescue.order.application.usecase;

import com.foodrescue.order.application.port.out.IOrderReviewRepository;
import com.foodrescue.order.domain.model.ReviewEligibility;

public class CheckReviewEligibilityUseCase {
    private final IOrderReviewRepository orders;

    public CheckReviewEligibilityUseCase(IOrderReviewRepository orders) { this.orders = orders; }

    public ReviewEligibility execute(Long orderId, Long customerId) {
        return orders.findEligibility(orderId, customerId)
                .orElse(new ReviewEligibility(false, orderId, null, customerId, null));
    }
}
