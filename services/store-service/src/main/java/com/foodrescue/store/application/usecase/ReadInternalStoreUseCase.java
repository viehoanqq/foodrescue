package com.foodrescue.store.application.usecase;

import com.foodrescue.store.application.port.out.IInternalStoreRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreInternalView;

public class ReadInternalStoreUseCase {
    private final IInternalStoreRepository stores;

    public ReadInternalStoreUseCase(IInternalStoreRepository stores) {
        this.stores = stores;
    }

    public StoreInternalView.CheckoutPolicy checkoutPolicy(Long storeId) {
        validateId(storeId);
        return stores.findCheckoutPolicy(storeId)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
    }

    public StoreInternalView.PayoutAccount payoutAccount(Long storeId) {
        validateId(storeId);
        return stores.findPayoutAccount(storeId)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
    }

    public StoreInternalView.Counts count() {
        return stores.countByStatus();
    }

    private void validateId(Long storeId) {
        if (storeId == null || storeId <= 0)
            throw new StoreException(400, "INVALID_STORE_ID", "Mã cửa hàng không hợp lệ");
    }
}
