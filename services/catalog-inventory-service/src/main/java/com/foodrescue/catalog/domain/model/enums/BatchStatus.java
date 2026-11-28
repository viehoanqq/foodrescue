package com.foodrescue.catalog.domain.model.enums;

import java.util.Locale;
import java.util.Optional;

/** Trạng thái vòng đời túi giải cứu (rescue_batches.status). */
public enum BatchStatus {
    AVAILABLE,
    SOLD_OUT,
    CANCELLED,
    EXPIRED;

    public boolean isAvailable() {
        return this == AVAILABLE;
    }

    public boolean isStockAdjustable() {
        return this == AVAILABLE || this == SOLD_OUT;
    }

    public boolean isCancellable() {
        return this == AVAILABLE || this == SOLD_OUT;
    }

    public boolean isPubliclyVisible(int remainingQuantity) {
        return (this == AVAILABLE && remainingQuantity > 0)
                || (this == SOLD_OUT && remainingQuantity == 0);
    }

    public static BatchStatus fromRemainingQuantity(int remainingQuantity) {
        return remainingQuantity == 0 ? SOLD_OUT : AVAILABLE;
    }

    public static Optional<BatchStatus> fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(BatchStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
    }
}
