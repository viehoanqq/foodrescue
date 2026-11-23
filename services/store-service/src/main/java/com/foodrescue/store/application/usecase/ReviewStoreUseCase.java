package com.foodrescue.store.application.usecase;

import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreView;
import java.math.BigDecimal;

public class ReviewStoreUseCase {
    private final IStoreRepository stores;
    public ReviewStoreUseCase(IStoreRepository stores) { this.stores = stores; }

    public StoreView.Admin approve(Long id, Long actorId, BigDecimal commissionRate) {
        if (commissionRate == null) commissionRate = new BigDecimal("15.00");
        if (commissionRate.signum() < 0 || commissionRate.compareTo(new BigDecimal("50.00")) > 0)
            throw new StoreException(400, "INVALID_COMMISSION", "Hoa hồng phải từ 0 đến 50%");
        stores.findForAdminById(id).orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
        return stores.changeStatus(id, "PENDING", "APPROVED", commissionRate, null, actorId);
    }

    public StoreView.Admin reject(Long id, Long actorId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 255)
            throw new StoreException(400, "INVALID_REASON", "Cần lý do từ chối tối đa 255 ký tự");
        stores.findForAdminById(id).orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
        return stores.changeStatus(id, "PENDING", "REJECTED", null, reason.trim(), actorId);
    }

    public StoreView.Admin setLocked(Long id, Long actorId, boolean locked, String reason) {
        stores.findForAdminById(id).orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
        if (locked && (reason == null || reason.isBlank() || reason.length() > 255))
            throw new StoreException(400, "INVALID_REASON", "Cần lý do khóa tối đa 255 ký tự");
        return stores.changeStatus(id, locked ? "APPROVED" : "LOCKED", locked ? "LOCKED" : "APPROVED",
                null, locked ? reason.trim() : null, actorId);
    }

    public StoreView.Admin commission(Long id, Long actorId, BigDecimal commissionRate) {
        if (commissionRate == null || commissionRate.signum() < 0
                || commissionRate.compareTo(new BigDecimal("50.00")) > 0)
            throw new StoreException(400, "INVALID_COMMISSION", "Hoa hồng phải từ 0 đến 50%");
        stores.findForAdminById(id).orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
        return stores.changeCommission(id, commissionRate, actorId);
    }
}
