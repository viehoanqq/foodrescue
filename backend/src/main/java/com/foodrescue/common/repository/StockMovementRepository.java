package com.foodrescue.common.repository;

import com.foodrescue.common.entity.StockMovement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByBatchIdOrderByCreatedAtAscIdAsc(Long batchId);
}
