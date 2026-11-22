package com.foodrescue.store.application.port.out;

import com.foodrescue.store.domain.model.OwnedStore;
import java.util.Optional;

public interface OwnedStorePort {
    Optional<OwnedStore> findByOwnerId(Long ownerId);
}
