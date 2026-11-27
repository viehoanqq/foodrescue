package com.foodrescue.catalog.domain.model;

import java.util.Set;

/** Nội dung đã kiểm tra của store.status-changed.v1. */
public record StoreStatusChange(long storeId, long ownerId, String status, long version) {
    private static final Set<String> STATUSES = Set.of("APPROVED", "REJECTED", "LOCKED");

    public StoreStatusChange {
        if (storeId <= 0 || ownerId <= 0 || version <= 0 || status == null || !STATUSES.contains(status)) {
            throw new IllegalArgumentException("Invalid store status change");
        }
    }
}
