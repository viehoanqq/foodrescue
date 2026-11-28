package com.foodrescue.catalog.application.port.out;

import com.foodrescue.catalog.domain.model.CatalogStoreSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface IStoreDirectoryPort {
    List<CatalogStoreSummary> findApprovedStores(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm);
    Optional<CatalogStoreSummary> findApprovedStore(Long storeId);
}
