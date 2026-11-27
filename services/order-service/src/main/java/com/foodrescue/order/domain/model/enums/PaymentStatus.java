package com.foodrescue.order.domain.model.enums;

import java.util.Locale;
import java.util.Optional;

/** Trạng thái thanh toán / hoàn tiền của đơn hàng (orders.payment_status). */
public enum PaymentStatus {
    UNPAID,
    PAID,
    REFUND_PENDING,
    REFUNDED;

    public boolean isPaid() {
        return this == PAID;
    }

    public boolean isRefundInitiated() {
        return this == REFUND_PENDING || this == REFUNDED;
    }

    public static Optional<PaymentStatus> fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(PaymentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
    }
}
