package com.foodrescue.catalog.application.usecase;

import com.foodrescue.catalog.application.port.out.IStoreOwnerLookupPort;
import com.foodrescue.catalog.application.port.out.IStoreProjectionRepository;
import com.foodrescue.catalog.application.port.out.IStoreStatusPort;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.domain.model.Actor;
import com.foodrescue.catalog.domain.model.CatalogStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CatalogStoreAccess {
    private final IStoreProjectionRepository projections;
    private final IStoreOwnerLookupPort owners;
    private final IStoreStatusPort statuses;

    public CatalogStoreAccess(IStoreProjectionRepository projections, IStoreOwnerLookupPort owners,
                              IStoreStatusPort statuses) {
        this.projections = projections;
        this.owners = owners;
        this.statuses = statuses;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Long requireManagedStoreId(Actor actor) {
        CatalogStore store;
        if ("STORE_STAFF".equals(actor.role())) {
            if (actor.storeId() == null) {
                throw CatalogException.forbidden("STORE_NOT_ASSIGNED", "Tài khoản chưa thuộc cửa hàng nào");
            }
            store = projections.findByStoreId(actor.storeId()).orElseThrow(this::storeNotApproved);
        } else if ("STORE_OWNER".equals(actor.role())) {
            store = projections.findByOwnerId(actor.userId())
                    .or(() -> owners.findByOwnerId(actor.userId()))
                    .orElseThrow(() -> CatalogException.forbidden(
                            "STORE_NOT_FOUND", "Tài khoản chưa có cửa hàng được duyệt"));
        } else {
            throw CatalogException.forbidden("FORBIDDEN", "Bạn không có quyền thực hiện thao tác này");
        }
        CatalogStore current = statuses.findByStoreId(store.storeId()).orElseThrow(this::storeNotApproved);
        if (!current.approved()) throw storeNotApproved();
        return current.storeId();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Long requireApprovedStoreId(Long storeId) {
        if (storeId == null || storeId < 1) {
            throw CatalogException.badRequest("VALIDATION_ERROR", "storeId phải lớn hơn 0");
        }
        CatalogStore current = statuses.findByStoreId(storeId)
                .filter(CatalogStore::approved)
                .orElseThrow(() -> CatalogException.notFound("STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
        return current.storeId();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void requireExistingStore(Long storeId) {
        statuses.findByStoreId(storeId).orElseThrow(() ->
                CatalogException.notFound("STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
    }

    private CatalogException storeNotApproved() {
        return CatalogException.forbidden("STORE_NOT_APPROVED", "Cửa hàng chưa được duyệt hoặc đang bị khóa");
    }
}
