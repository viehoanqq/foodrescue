package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.foodrescue.catalog.application.port.out.IBatchStorePort;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.RescueBatchEntity;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaBatchStoreAdapter implements IBatchStorePort {
    private final ISpringDataRescueBatchRepository batches;

    public JpaBatchStoreAdapter(ISpringDataRescueBatchRepository batches) {
        this.batches = batches;
    }

    @Override
    @Transactional(readOnly = true)
    public Long requireSingleStore(List<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty() || batchIds.contains(null)
                || batchIds.stream().anyMatch(id -> id <= 0)
                || new HashSet<>(batchIds).size() != batchIds.size()) {
            throw CatalogException.badRequest("VALIDATION_ERROR", "Invalid batch IDs");
        }
        List<RescueBatchEntity> found = batches.findAllById(batchIds);
        if (found.size() != batchIds.size()
                || found.stream().anyMatch(batch -> batch.getDeletedAt() != null)) {
            throw CatalogException.notFound("BATCH_NOT_FOUND", "Batch not found");
        }
        Long storeId = found.get(0).getStoreId();
        if (found.stream().anyMatch(batch -> !storeId.equals(batch.getStoreId()))) {
            throw CatalogException.conflict("MULTI_STORE_CHECKOUT", "Batches belong to different stores");
        }
        return storeId;
    }
}
