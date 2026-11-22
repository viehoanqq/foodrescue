package com.foodrescue.store.domain.model;

public record OwnedStore(Long id, Long ownerId, String status) {
    public boolean isApproved() { return "APPROVED".equals(status); }
}
