package com.foodrescue.store.application.port.out;

import com.foodrescue.store.domain.model.OwnedStore;
import com.foodrescue.store.domain.model.StoreRegistration;
import com.foodrescue.store.domain.model.StoreView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface IStoreRepository {
    Optional<OwnedStore> findByOwnerId(Long ownerId);
    Optional<StoreView.Registration> findRegistration(Long ownerId);
    Optional<StoreView.Profile> findProfileByOwnerId(Long ownerId);
    Optional<StoreView.Detail> findApprovedById(Long storeId);
    StoreView.Page findApproved(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm, int page, int size);
    Registered save(Long ownerId, StoreRegistration data, String licenseUrl, String imageUrl);
    record Registered(Long storeId, LocalDateTime createdAt) {}
}
