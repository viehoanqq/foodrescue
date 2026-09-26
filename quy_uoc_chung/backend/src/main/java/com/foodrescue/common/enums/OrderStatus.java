package com.foodrescue.common.enums;

/** orders.status */
public enum OrderStatus {
    PENDING_PAYMENT, CONFIRMED, COMPLETED, CANCELLED, EXPIRED, NO_SHOW;

    /** Đơn đã chốt: được tính tiền cho cửa hàng và được đưa vào kỳ chi trả. */
    public boolean isSettled() {
        return this == COMPLETED || this == NO_SHOW;
    }
}
