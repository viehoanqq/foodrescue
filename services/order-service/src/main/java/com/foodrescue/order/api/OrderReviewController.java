package com.foodrescue.order.api;

import com.foodrescue.buildingblocks.http.InternalKeyVerifier;
import com.foodrescue.order.application.usecase.CheckReviewEligibilityUseCase;
import com.foodrescue.order.domain.model.ReviewEligibility;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderReviewController {
    private final CheckReviewEligibilityUseCase eligibility;
    private final InternalKeyVerifier verifier;

    public OrderReviewController(CheckReviewEligibilityUseCase eligibility,
                                 @Value("${app.internal-api-key}") String key) {
        this.eligibility = eligibility;
        this.verifier = new InternalKeyVerifier(key);
    }

    @GetMapping("/internal/orders/{id}/review-eligibility")
    public ReviewEligibility check(@RequestHeader("X-Internal-Key") String key,
                                   @PathVariable Long id, @RequestParam Long customerId) {
        verifier.verify(key);
        return eligibility.execute(id, customerId);
    }
}
