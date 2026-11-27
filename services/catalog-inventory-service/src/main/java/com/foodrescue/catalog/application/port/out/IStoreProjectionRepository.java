package com.foodrescue.catalog.application.port.out;

import com.foodrescue.catalog.domain.model.CatalogStore;
import com.foodrescue.catalog.domain.model.StoreStatusChange;
import java.util.Optional;

public interface IStoreProjectionRepository {
    Optional<CatalogStore> findByStoreId(Long storeId);

    Optional<CatalogStore> findByOwnerId(Long ownerId);

    /** Chỉ ghi khi version mới hơn bản đang lưu; trả true nếu có thay đổi. */
    boolean applyIfNewer(StoreStatusChange change);
}
