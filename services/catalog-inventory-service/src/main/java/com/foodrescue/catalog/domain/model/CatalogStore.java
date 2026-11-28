package com.foodrescue.catalog.domain.model;

import com.foodrescue.catalog.domain.model.enums.StoreProjectionStatus;

/** Trạng thái cửa hàng Catalog đã nhận từ Store qua store.status-changed.v1 hoặc tra cứu nội bộ. */
public record CatalogStore(Long storeId, Long ownerId, String status, long version) {
    public boolean approved() {
        return StoreProjectionStatus.APPROVED.name().equals(status);
    }
}
