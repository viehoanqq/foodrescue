package com.foodrescue.store.application.usecase;

import com.foodrescue.store.application.port.out.IAdminStoreRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreView;
import com.foodrescue.store.domain.model.enums.StoreStatus;

public class ReadAdminStoreUseCase {
    private final IAdminStoreRepository stores;

    public ReadAdminStoreUseCase(IAdminStoreRepository stores) { this.stores = stores; }

    public StoreView.AdminPage list(String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new StoreException(400, "INVALID_PAGINATION", "Phân trang không hợp lệ");
        StoreStatus parsed;
        try {
            parsed = StoreStatus.fromNullable(status).orElse(null);
        } catch (IllegalArgumentException ex) {
            throw new StoreException(400, "INVALID_STATUS", "Trạng thái không hợp lệ");
        }
        return stores.findForAdmin(parsed, page, size);
    }

    public StoreView.Admin detail(Long id) {
        return stores.findForAdminById(id)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
    }
}
