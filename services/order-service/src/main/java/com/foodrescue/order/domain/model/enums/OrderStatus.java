package com.foodrescue.order.domain.model.enums;

import java.util.Locale;
import java.util.Optional;

/** Trạng thái vòng đời đơn hàng (orders.status). */
public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    NO_SHOW;

    public boolean canCancelByCustomer() {
        return this == PENDING_PAYMENT || this == CONFIRMED;
    }

    public boolean isTerminalForAdmin() {
        return this == CANCELLED || this == COMPLETED || this == NO_SHOW || this == EXPIRED;
    }

    public boolean isLatePaymentRefundable() {
        return this == CANCELLED || this == EXPIRED;
    }

    public boolean isActiveForBatchCancel() {
        return this == PENDING_PAYMENT || this == CONFIRMED;
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }

    public static Optional<OrderStatus> fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(OrderStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
    }
}
