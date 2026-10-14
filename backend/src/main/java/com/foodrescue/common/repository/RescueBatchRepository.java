package com.foodrescue.common.repository;

import com.foodrescue.common.entity.RescueBatch;
import com.foodrescue.common.enums.BatchStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RescueBatchRepository extends JpaRepository<RescueBatch, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM RescueBatch b WHERE b.id = :id")
    Optional<RescueBatch> findByIdForUpdate(@Param("id") Long id);

    List<RescueBatch> findByStatusAndPickupEndBefore(BatchStatus status, LocalDateTime time);

    List<RescueBatch> findByStoreId(Long storeId);
}