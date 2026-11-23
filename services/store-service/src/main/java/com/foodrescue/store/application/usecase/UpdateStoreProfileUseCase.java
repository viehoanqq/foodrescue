package com.foodrescue.store.application.usecase;

import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreProfileUpdate;
import com.foodrescue.store.domain.model.StoreView;
import java.util.Set;

public class UpdateStoreProfileUseCase {
    private final IStoreRepository stores;

    public UpdateStoreProfileUseCase(IStoreRepository stores) { this.stores = stores; }

    public StoreView.Profile execute(Long ownerId, StoreProfileUpdate input) {
        StoreProfileUpdate normalized = StoreProfileUpdates.validateAndNormalize(input);
        StoreView.Profile current = stores.findProfileByOwnerId(ownerId)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Bạn chưa có cửa hàng"));
        if (!Set.of("APPROVED", "LOCKED").contains(current.status()))
            throw new StoreException(409, "STORE_NOT_EDITABLE", "Cửa hàng chưa thể chỉnh sửa hồ sơ");
        return stores.updateProfile(ownerId, normalized);
    }
}
