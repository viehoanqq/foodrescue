package com.foodrescue.catalog.application.usecase;

import com.foodrescue.catalog.application.port.out.IReservationPort;
import com.foodrescue.catalog.application.port.out.IStoreStatusPort;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.domain.model.CatalogStore;
import com.foodrescue.catalog.domain.model.ReservationRequest;
import com.foodrescue.catalog.domain.model.ReservationResult;
import java.util.UUID;

public class ReservationService {
    private final IReservationPort reservations;
    private final IStoreStatusPort stores;

    public ReservationService(IReservationPort reservations, IStoreStatusPort stores) {
        this.reservations = reservations;
        this.stores = stores;
    }

    public ReservationResult reserve(ReservationRequest request) {
        CatalogStore store = stores.findByStoreId(request.storeId())
                .orElseThrow(() -> CatalogException.notFound("STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
        if (!store.approved()) {
            throw CatalogException.forbidden("STORE_NOT_APPROVED", "Cửa hàng chưa được duyệt hoặc đang bị khóa");
        }
        return reservations.reserve(request);
    }
    public ReservationResult release(UUID id) { return reservations.release(id); }
    public ReservationResult load(UUID id) { return reservations.load(id); }
}
