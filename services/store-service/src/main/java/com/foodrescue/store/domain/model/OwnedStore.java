package com.foodrescue.store.domain.model;

import com.foodrescue.store.domain.model.enums.StoreStatus;

public record OwnedStore(Long id, Long ownerId, StoreStatus status) {
    public boolean isApproved() { return status == StoreStatus.APPROVED; }
}
