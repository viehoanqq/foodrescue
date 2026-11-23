package com.foodrescue.store.application.usecase;

import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.application.port.out.IStaffIdentityPort;
import com.foodrescue.store.application.port.out.IStaffIdentityPort.CreatedStaff;
import com.foodrescue.store.application.port.out.IStaffIdentityPort.NewStaff;
import com.foodrescue.store.domain.exception.StoreException;

public class CreateStaffUseCase {
    private final IStoreRepository stores;
    private final IStaffIdentityPort identity;

    public CreateStaffUseCase(IStoreRepository stores, IStaffIdentityPort identity) {
        this.stores = stores;
        this.identity = identity;
    }

    public CreatedStaff execute(Long ownerId, NewStaff staff) {
        var store = stores.findByOwnerId(ownerId)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Bạn chưa có cửa hàng"));
        if (!ownerId.equals(store.ownerId())) {
            throw new StoreException(403, "STORE_OWNER_MISMATCH", "Bạn không sở hữu cửa hàng này");
        }
        if (!store.isApproved()) {
            throw new StoreException(403, "STORE_NOT_APPROVED", "Cửa hàng chưa được duyệt để tạo nhân viên");
        }
        return identity.create(ownerId, store.id(), staff);
    }
}
