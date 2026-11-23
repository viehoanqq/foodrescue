package com.foodrescue.order.domain.model;

public record ReviewEligibility(boolean eligible, Long orderId, Long storeId,
                                Long customerId, String status) {}
