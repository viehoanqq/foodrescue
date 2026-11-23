package com.foodrescue.store.application.usecase;

import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreView;
import java.math.BigDecimal;

public class ReadStoreUseCase {
    private final IStoreRepository stores;
    public ReadStoreUseCase(IStoreRepository stores) { this.stores = stores; }

    public StoreView.Registration registration(Long ownerId) {
        return stores.findRegistration(ownerId)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Bạn chưa đăng ký cửa hàng"));
    }

    public StoreView.Profile profile(Long ownerId) {
        return stores.findProfileByOwnerId(ownerId)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Bạn chưa có cửa hàng"));
    }

    public StoreView.Detail detail(Long storeId) {
        return stores.findApprovedById(storeId)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
    }

    public StoreView.Page list(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm,
                               int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new StoreException(400, "INVALID_PAGINATION", "Trang hoặc kích thước trang không hợp lệ");
        if ((latitude == null) != (longitude == null))
            throw new StoreException(400, "INVALID_LOCATION", "Cần truyền cả lat và lon");
        if (latitude == null && radiusKm != null)
            throw new StoreException(400, "INVALID_LOCATION", "Cần lat và lon khi lọc bán kính");
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0))
            throw new StoreException(400, "INVALID_LOCATION", "Tọa độ không hợp lệ");
        if (radiusKm != null && (radiusKm.signum() <= 0 || radiusKm.compareTo(BigDecimal.valueOf(100)) > 0))
            throw new StoreException(400, "INVALID_RADIUS", "Bán kính phải lớn hơn 0 và không quá 100 km");
        return stores.findApproved(latitude, longitude, radiusKm, page, size);
    }

    public StoreView.AdminPage adminList(String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new StoreException(400, "INVALID_PAGINATION", "Phân trang không hợp lệ");
        if (status != null && !java.util.Set.of("PENDING", "APPROVED", "REJECTED", "LOCKED").contains(status))
            throw new StoreException(400, "INVALID_STATUS", "Trạng thái không hợp lệ");
        return stores.findForAdmin(status, page, size);
    }

    public StoreView.Admin adminDetail(Long id) {
        return stores.findForAdminById(id)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
    }
}
