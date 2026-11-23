package com.foodrescue.store.application.usecase;

import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.application.port.out.IStaffIdentityPort;
import com.foodrescue.store.application.port.out.IStaffIdentityPort.CreatedStaff;
import com.foodrescue.store.application.port.out.IStaffIdentityPort.NewStaff;
import com.foodrescue.store.application.port.out.IStaffIdentityPort.StaffPage;
import com.foodrescue.store.application.port.out.IStaffIdentityPort.StaffStatus;
import com.foodrescue.store.domain.model.OwnedStore;
import com.foodrescue.store.domain.exception.StoreException;

public class StoreStaffUseCase {
    private final IStoreRepository stores;
    private final IStaffIdentityPort identity;

    public StoreStaffUseCase(IStoreRepository stores, IStaffIdentityPort identity) {
        this.stores = stores;
        this.identity = identity;
    }

    public CreatedStaff execute(Long ownerId, NewStaff staff) {
        return identity.create(ownerId, verifiedStore(ownerId).id(), staff);
    }

    public StaffPage list(Long ownerId, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new StoreException(400, "INVALID_PAGINATION", "Phân trang không hợp lệ");
        return identity.list(ownerId, verifiedStore(ownerId).id(), page, size);
    }

    public StaffStatus setLocked(Long ownerId, Long staffId, boolean locked) {
        if (staffId == null || staffId <= 0)
            throw new StoreException(400, "INVALID_STAFF_ID", "Mã nhân viên không hợp lệ");
        return identity.setLocked(ownerId, verifiedStore(ownerId).id(), staffId, locked);
    }

    private OwnedStore verifiedStore(Long ownerId) {
        var store = stores.findByOwnerId(ownerId)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Bạn chưa có cửa hàng"));
        if (!ownerId.equals(store.ownerId())) {
            throw new StoreException(403, "STORE_OWNER_MISMATCH", "Bạn không sở hữu cửa hàng này");
        }
        if (!store.isApproved()) {
            throw new StoreException(403, "STORE_NOT_APPROVED", "Cửa hàng chưa được duyệt để tạo nhân viên");
        }
        return store;
    }
}
