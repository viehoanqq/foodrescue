package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.StockReservationEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface ISpringDataStockReservationRepository extends JpaRepository<StockReservationEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<StockReservationEntity> findForUpdateByReservationId(UUID reservationId);
}
