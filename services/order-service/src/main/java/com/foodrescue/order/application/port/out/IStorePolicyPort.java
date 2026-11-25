package com.foodrescue.order.application.port.out;

import java.math.BigDecimal;

public interface IStorePolicyPort {
    StoreCheckoutPolicy getPolicy(Long storeId);
    record StoreCheckoutPolicy(Long storeId, String status, BigDecimal commissionRate) {}
}
