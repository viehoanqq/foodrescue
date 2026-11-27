package com.foodrescue.order.application.port.out;

public interface ICheckoutIdempotencyPort {
    Long claim(Long customerId, String key);
    void complete(Long customerId, String key, Long orderId);
}
