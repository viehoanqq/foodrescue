package com.foodrescue.order.domain.model.enums;

import java.util.Optional;

/** Trạng thái giữ kho trả về từ catalog-inventory-service. */
public enum ReservationStatus {
    HELD,
    COMMITTED,
    RELEASED,
    EXPIRED;

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
