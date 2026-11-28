package com.foodrescue.catalog.domain.model;

/** Trạng thái cửa hàng Catalog đã nhận từ Store qua store.status-changed.v1. */
public record CatalogStore(Long storeId, Long ownerId, String status, long version) {
    public boolean approved() {
        return "APPROVED".equals(status);
    }
}
