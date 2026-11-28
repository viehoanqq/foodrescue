package com.foodrescue.catalog.domain.model.enums;

import java.util.Optional;

/** Trạng thái cửa hàng đồng bộ sang Catalog qua store.status-changed.v1 (store_status_projection.status). */
public enum StoreProjectionStatus {
    APPROVED,
    REJECTED,
    LOCKED;

    public boolean isApproved() {
        return this == APPROVED;
    }

    public static StoreProjectionStatus parseRequired(String raw) {
        return tryParse(raw)
                .orElseThrow(() -> new IllegalArgumentException("Invalid store projection status: " + raw));
    }

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
