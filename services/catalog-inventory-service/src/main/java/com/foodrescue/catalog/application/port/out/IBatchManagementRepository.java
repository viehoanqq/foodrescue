package com.foodrescue.catalog.application.port.out;

import com.foodrescue.catalog.domain.model.RescueBatch;
import com.foodrescue.catalog.domain.model.StockMovement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IBatchManagementRepository {
    List<RescueBatch> findActiveByStore(Long storeId);
    Page<RescueBatch> findAdminByStore(Long storeId, Pageable pageable);
    Page<RescueBatch> findAllAdmin(Pageable pageable);
    Optional<RescueBatch> findForUpdate(Long storeId, Long batchId);
    Optional<RescueBatch> findForRead(Long storeId, Long batchId);
    Optional<RescueBatch> findAdminForUpdate(Long batchId);
    boolean activeCategoryExists(Long storeId, Long categoryId);
    boolean categoryBelongsToStore(Long storeId, Long categoryId);
    boolean hasHeldReservations(Long batchId);
    RescueBatch save(RescueBatch batch);
    void appendMovement(StockMovement movement);
    Page<StockMovement> movements(Long batchId, Pageable pageable);
}
