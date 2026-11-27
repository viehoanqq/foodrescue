package com.foodrescue.order.api.dto;

public record ReviewEligibilityResponse(
        Long orderId,
        Long customerId,
        Long storeId,
        boolean eligible,
        String status
) {}
