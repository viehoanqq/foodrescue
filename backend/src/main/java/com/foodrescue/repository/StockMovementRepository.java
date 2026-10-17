package com.foodrescue.repository;

import com.foodrescue.entity.StockMovement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByBatchIdOrderByCreatedAtAscIdAsc(Long batchId);
}
