package com.foodrescue.store.application.port.out;

import com.foodrescue.store.domain.model.StoreInternalView;
import java.util.Optional;

public interface IInternalStoreRepository {
    Optional<StoreInternalView.CheckoutPolicy> findCheckoutPolicy(Long storeId);
    Optional<StoreInternalView.PayoutAccount> findPayoutAccount(Long storeId);
    Optional<StoreInternalView.OwnerStore> findByOwnerId(Long ownerId);
    StoreInternalView.Counts countByStatus();
}
