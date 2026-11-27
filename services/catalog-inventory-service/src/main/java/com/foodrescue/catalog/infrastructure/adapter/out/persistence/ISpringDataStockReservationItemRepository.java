package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.StockReservationItemEntity;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.StockReservationItemEntityId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ISpringDataStockReservationItemRepository
        extends JpaRepository<StockReservationItemEntity, StockReservationItemEntityId> {
    List<StockReservationItemEntity> findByReservationIdOrderByBatchId(UUID reservationId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM stock_reservation_items i
                JOIN stock_reservations r ON r.reservation_id = i.reservation_id
                WHERE i.batch_id = :batchId AND r.status = 'HELD'
            )
            """, nativeQuery = true)
    boolean hasHeldReservationForBatch(@Param("batchId") Long batchId);
}
