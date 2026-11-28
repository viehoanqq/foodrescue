package com.foodrescue.catalog.domain.model.enums;

import java.util.Optional;

/** Lý do biến động sổ kho append-only (stock_movements.reason). */
public enum StockMovementReason {
    LISTED,
    ADDED,
    REMOVED,
    ORDERED,
    RETURNED;

    public boolean isManualAdjustment() {
        return this == ADDED || this == REMOVED;
    }

    public static Optional<StockMovementReason> parseManualAdjustment(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return switch (raw.trim()) {
            case "ADDED" -> Optional.of(ADDED);
            case "REMOVED" -> Optional.of(REMOVED);
            default -> Optional.empty();
        };
    }
}
