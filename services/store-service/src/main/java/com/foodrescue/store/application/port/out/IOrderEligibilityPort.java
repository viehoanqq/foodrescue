package com.foodrescue.store.application.port.out;

public interface IOrderEligibilityPort {
    Eligibility check(Long orderId, Long customerId);

    record Eligibility(boolean eligible, Long orderId, Long storeId, Long customerId, String status) {}
}
