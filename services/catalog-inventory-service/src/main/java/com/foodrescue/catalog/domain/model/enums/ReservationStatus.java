package com.foodrescue.catalog.domain.model.enums;

import java.util.Optional;

/** Trạng thái giữ tồn kho cho đơn hàng (stock_reservations.status). */
public enum ReservationStatus {
    HELD,
    COMMITTED,
    RELEASED,
    EXPIRED;

    public boolean isHeld() {
        return this == HELD;
    }

    public boolean isCommitted() {
        return this == COMMITTED;
    }

    public boolean isReleased() {
        return this == RELEASED;
    }

    public boolean isExpired() {
        return this == EXPIRED;
    }

    public boolean canCommit() {
        return this == HELD;
    }

    public boolean canRelease() {
        return this == HELD || this == COMMITTED;
    }

    public static Optional<ReservationStatus> tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ReservationStatus.valueOf(raw.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
