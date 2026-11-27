package com.foodrescue.order.api;

import com.foodrescue.buildingblocks.http.InternalKeyVerifier;
import com.foodrescue.order.api.dto.PaymentIntentResponse;
import com.foodrescue.order.api.dto.ReviewEligibilityResponse;
import com.foodrescue.order.application.usecase.InternalOrderUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {
    private final InternalOrderUseCase internalOrderUseCase;
    private final InternalKeyVerifier verifier;

    public InternalOrderController(
            InternalOrderUseCase internalOrderUseCase,
            @Value("${app.internal-api-key:}") String internalKey) {
        this.internalOrderUseCase = internalOrderUseCase;
        this.verifier = new InternalKeyVerifier(internalKey);
    }

    @GetMapping("/{id}/payment-intent")
    public PaymentIntentResponse getPaymentIntent(
            @RequestHeader("X-Internal-Key") String key,
            @PathVariable Long id) {
        verifier.verify(key);
        return internalOrderUseCase.getPaymentIntent(id);
    }

    @GetMapping("/{id}/review-eligibility")
    public ReviewEligibilityResponse getReviewEligibility(
            @RequestHeader("X-Internal-Key") String key,
            @PathVariable Long id,
            @RequestParam(required = false) Long customerId) {
        verifier.verify(key);
        return internalOrderUseCase.getReviewEligibility(id, customerId);
    }
}
