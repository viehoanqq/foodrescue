package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.foodrescue.catalog.application.port.out.IStoreProjectionRepository;
import com.foodrescue.catalog.domain.model.CatalogStore;
import com.foodrescue.catalog.domain.model.StoreStatusChange;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.StoreProjectionEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaStoreProjectionRepository implements IStoreProjectionRepository {
    private final ISpringDataStoreProjectionRepository projections;

    public JpaStoreProjectionRepository(ISpringDataStoreProjectionRepository projections) {
        this.projections = projections;
    }

    @Override
    public Optional<CatalogStore> findByStoreId(Long storeId) {
        return projections.findById(storeId).map(this::toDomain);
    }

    @Override
    public Optional<CatalogStore> findByOwnerId(Long ownerId) {
        return projections.findByOwnerId(ownerId).map(this::toDomain);
    }

    @Override
    public boolean applyIfNewer(StoreStatusChange change) {
        Optional<StoreProjectionEntity> found = projections.findForUpdateByStoreId(change.storeId());
        if (found.isEmpty()) {
            projections.saveAndFlush(new StoreProjectionEntity(change.storeId(), change.ownerId(),
                    change.status(), change.version()));
            return true;
        }
        StoreProjectionEntity entity = found.get();
        if (change.version() <= entity.getAggregateVersion()) return false; // event cũ/đến muộn
        entity.setOwnerId(change.ownerId());
        entity.setStatus(change.status());
        entity.setAggregateVersion(change.version());
        entity.setUpdatedAt(Instant.now());
        projections.saveAndFlush(entity);
        return true;
    }

    private CatalogStore toDomain(StoreProjectionEntity e) {
        return new CatalogStore(e.getStoreId(), e.getOwnerId(),
                e.getStatus() != null ? e.getStatus().name() : null, e.getAggregateVersion());
    }
}
