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
        validatePositiveId(storeId, "INVALID_STORE_ID", "Mã cửa hàng không hợp lệ");
        return stores.findCheckoutPolicy(storeId)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
    }

    public StoreInternalView.PayoutAccount payoutAccount(Long storeId) {
        validatePositiveId(storeId, "INVALID_STORE_ID", "Mã cửa hàng không hợp lệ");
        return stores.findPayoutAccount(storeId)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
    }

    public StoreInternalView.OwnerStore byOwner(Long ownerId) {
        validatePositiveId(ownerId, "INVALID_OWNER_ID", "Mã chủ cửa hàng không hợp lệ");
        return stores.findByOwnerId(ownerId)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
    }

    public StoreInternalView.Counts count() {
        return stores.countByStatus();
    }

    private void validatePositiveId(Long id, String code, String message) {
        if (id == null || id <= 0)
            throw new StoreException(400, code, message);
    }
}
