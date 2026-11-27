package com.foodrescue.store.infrastructure.adapter.out.persistence;

import com.foodrescue.store.application.port.out.IInternalStoreRepository;
import com.foodrescue.store.domain.model.StoreInternalView;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaInternalStoreRepository implements IInternalStoreRepository {
    private final IJpaStoreRepository stores;

    public JpaInternalStoreRepository(IJpaStoreRepository stores) { this.stores = stores; }

    @Override
    public Optional<StoreInternalView.CheckoutPolicy> findCheckoutPolicy(Long storeId) {
        return stores.findById(storeId).map(s -> new StoreInternalView.CheckoutPolicy(
                s.getId(), s.getStatus(), s.getCommissionRate(), s.getAggregateVersion(), s.getUpdatedAt()));
    }

    @Override
    public Optional<StoreInternalView.PayoutAccount> findPayoutAccount(Long storeId) {
        return stores.findById(storeId).map(s -> new StoreInternalView.PayoutAccount(
                s.getId(), s.getStatus(), s.getBankName(), s.getBankAccountNo(),
                s.getBankAccountName(), s.getUpdatedAt()));
    }

    @Override
    public StoreInternalView.Counts countByStatus() {
        return new StoreInternalView.Counts(stores.countByStatus("APPROVED"),
                stores.countByStatus("LOCKED"), stores.countByStatus("PENDING"));
    }
}
