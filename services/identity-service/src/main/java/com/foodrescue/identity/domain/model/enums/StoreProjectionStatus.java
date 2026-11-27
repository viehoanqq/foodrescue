package com.foodrescue.identity.domain.model.enums;

import java.util.Optional;

/** Trạng thái cửa hàng đồng bộ sang Identity qua sự kiện store.status-changed.v1. */
public enum StoreProjectionStatus {
    APPROVED,
    REJECTED,
    LOCKED;

    public static Optional<StoreProjectionStatus> tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(StoreProjectionStatus.valueOf(raw.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
