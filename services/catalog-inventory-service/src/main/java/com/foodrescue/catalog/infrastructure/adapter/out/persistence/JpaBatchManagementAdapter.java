package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.foodrescue.catalog.application.port.out.IBatchManagementRepository;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.domain.model.RescueBatch;
import com.foodrescue.catalog.domain.model.StockMovement;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.RescueBatchEntity;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.StockMovementEntity;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaBatchManagementAdapter implements IBatchManagementRepository {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ISpringDataRescueBatchRepository batches;
    private final ISpringDataCategoryRepository categories;
    private final ISpringDataStockMovementRepository movements;
    private final ISpringDataStockReservationItemRepository reservationItems;

    public JpaBatchManagementAdapter(ISpringDataRescueBatchRepository batches,
            ISpringDataCategoryRepository categories, ISpringDataStockMovementRepository movements,
            ISpringDataStockReservationItemRepository reservationItems) {
        this.batches = batches;
        this.categories = categories;
        this.movements = movements;
        this.reservationItems = reservationItems;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RescueBatch> findActiveByStore(Long storeId) {
        return batches.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(storeId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RescueBatch> findAdminByStore(Long storeId, Pageable pageable) {
        return batches.findByStoreIdOrderByCreatedAtDesc(storeId, pageable).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RescueBatch> findAllAdmin(Pageable pageable) {
        return batches.findAllByOrderByCreatedAtDesc(pageable).map(this::toDomain);
    }

    @Override
    public Optional<RescueBatch> findForUpdate(Long storeId, Long batchId) {
        return batches.findForUpdateByIdAndStoreIdAndDeletedAtIsNull(batchId, storeId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RescueBatch> findForRead(Long storeId, Long batchId) {
        return batches.findByIdAndStoreIdAndDeletedAtIsNull(batchId, storeId).map(this::toDomain);
    }

    @Override
    public Optional<RescueBatch> findAdminForUpdate(Long batchId) {
        return batches.findForUpdateByIdAndDeletedAtIsNull(batchId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean activeCategoryExists(Long storeId, Long categoryId) {
        return categories.existsByIdAndStoreIdAndArchivedAtIsNull(categoryId, storeId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean categoryBelongsToStore(Long storeId, Long categoryId) {
        return categories.existsByIdAndStoreId(categoryId, storeId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasHeldReservations(Long batchId) {
        return reservationItems.hasHeldReservationForBatch(batchId);
    }

    @Override
    public RescueBatch save(RescueBatch batch) {
        RescueBatchEntity entity = batch.id() == null
                ? new RescueBatchEntity()
                : batches.findById(batch.id()).orElseThrow(() ->
                        CatalogException.notFound("BATCH_NOT_FOUND", "Không tìm thấy túi giải cứu"));
        entity.setStoreId(batch.storeId());
        entity.setCategoryId(batch.categoryId());
        entity.setTitle(batch.title());
        entity.setDescription(batch.description());
        entity.setImageUrl(batch.imageUrl());
        entity.setOriginalPrice(batch.originalPrice());
        entity.setRescuePrice(batch.rescuePrice());
        entity.setQuantity(batch.quantity());
        entity.setRemainingQuantity(batch.remainingQuantity());
        entity.setPickupStart(batch.pickupStart());
        entity.setPickupEnd(batch.pickupEnd());
        entity.setStatus(batch.status());
        entity.setCreatedAt(batch.createdAt() == null ? LocalDateTime.now(VIETNAM) : batch.createdAt());
        entity.setAggregateVersion(batch.version());
        if (batch.deleted()) entity.setDeletedAt(OffsetDateTime.now(VIETNAM));
        return toDomain(batches.saveAndFlush(entity));
    }

    @Override
    public void appendMovement(StockMovement movement) {
        StockMovementEntity entity = new StockMovementEntity();
        entity.setBatchId(movement.batchId());
        entity.setChangeQty(movement.changeQty());
        entity.setReason(movement.reason());
        entity.setOrderId(movement.orderId());
        entity.setActorId(movement.actorId());
        entity.setRemainingAfter(movement.remainingAfter());
        entity.setNote(movement.note());
        entity.setCreatedAt(movement.createdAt() == null ? LocalDateTime.now(VIETNAM) : movement.createdAt());
        movements.saveAndFlush(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockMovement> movements(Long batchId, Pageable pageable) {
        return movements.findByBatchIdOrderByCreatedAtDescIdDesc(batchId, pageable).map(this::toDomain);
    }

    private RescueBatch toDomain(RescueBatchEntity entity) {
        return new RescueBatch(entity.getId(), entity.getStoreId(), entity.getCategoryId(), entity.getTitle(),
                entity.getDescription(), entity.getImageUrl(), entity.getOriginalPrice(), entity.getRescuePrice(),
                entity.getQuantity(), entity.getRemainingQuantity(), entity.getPickupStart(), entity.getPickupEnd(),
                entity.getStatus(), entity.getCreatedAt(), entity.getAggregateVersion(),
                entity.getDeletedAt() != null);
    }

    private StockMovement toDomain(StockMovementEntity entity) {
        return new StockMovement(entity.getId(), entity.getBatchId(), entity.getChangeQty(), entity.getReason(),
                entity.getOrderId(), entity.getActorId(), entity.getRemainingAfter(), entity.getNote(),
                entity.getCreatedAt());
    }
}
