package com.foodrescue.catalog.domain.model;

import com.foodrescue.catalog.domain.model.enums.StoreProjectionStatus;

/** Nội dung đã kiểm tra của store.status-changed.v1. */
public record StoreStatusChange(long storeId, long ownerId, StoreProjectionStatus status, long version) {
    public StoreStatusChange {
        if (storeId <= 0 || ownerId <= 0 || version <= 0 || status == null) {
            throw new IllegalArgumentException("Invalid store status change");
        }
    }
}
