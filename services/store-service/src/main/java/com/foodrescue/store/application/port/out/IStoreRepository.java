package com.foodrescue.store.application.port.out;

import com.foodrescue.store.domain.model.OwnedStore;
import com.foodrescue.store.domain.model.StoreRegistration;
import java.time.LocalDateTime;
import java.util.Optional;

public interface IStoreRepository {
    Optional<OwnedStore> findByOwnerId(Long ownerId);
    Registered save(Long ownerId, StoreRegistration data, String licenseUrl, String imageUrl);
    record Registered(Long storeId, LocalDateTime createdAt) {}
}
