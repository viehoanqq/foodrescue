package com.foodrescue.order.infrastructure.adapter.out.persistence;

import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.PendingInventoryReleaseEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataPendingReleaseRepository extends JpaRepository<PendingInventoryReleaseEntity, Long> {
    Optional<PendingInventoryReleaseEntity> findByReservationId(String reservationId);

    @Query("SELECT p FROM PendingInventoryReleaseEntity p WHERE p.nextAttemptAt <= :now AND p.attempts < 10 ORDER BY p.nextAttemptAt ASC")
    List<PendingInventoryReleaseEntity> findDueForRetry(@Param("now") LocalDateTime now);
}
