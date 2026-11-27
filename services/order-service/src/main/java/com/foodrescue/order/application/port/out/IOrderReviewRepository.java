package com.foodrescue.order.application.port.out;

import com.foodrescue.order.domain.model.ReviewEligibility;
import java.util.Optional;

public interface IOrderReviewRepository {
    Optional<ReviewEligibility> findEligibility(Long orderId, Long customerId);
}
