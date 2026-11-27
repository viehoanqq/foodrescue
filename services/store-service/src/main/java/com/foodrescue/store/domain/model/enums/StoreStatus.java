package com.foodrescue.store.domain.model.enums;

import java.util.Locale;
import java.util.Optional;

/** Vòng đời xét duyệt và vận hành của cửa hàng (stores.status). */
public enum StoreStatus {
    PENDING,
    APPROVED,
    REJECTED,
    LOCKED;

    public boolean isApproved() {
        return this == APPROVED;
    }

    public boolean isProfileEditable() {
        return this == APPROVED || this == LOCKED;
    }

    public static Optional<StoreStatus> fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(StoreStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
    }
}
