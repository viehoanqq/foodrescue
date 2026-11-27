package com.foodrescue.catalog.application.port.out;

import com.foodrescue.catalog.domain.model.CatalogStore;
import java.util.Optional;

public interface IStoreStatusPort {
    Optional<CatalogStore> findByStoreId(Long storeId);
}
