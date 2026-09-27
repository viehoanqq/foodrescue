// RescueBatchRepository.java
package com.foodrescue.common.repository;

import com.foodrescue.common.entity.RescueBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RescueBatchRepository extends JpaRepository<RescueBatch, Long> {
}