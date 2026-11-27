package com.foodrescue.finance.infrastructure.adapter.out.persistence;

import com.foodrescue.finance.infrastructure.adapter.out.persistence.entity.*;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface PayoutRepository extends JpaRepository<PayoutEntity, Long> {

    Optional<PayoutEntity> findByCreatedByAndIdempotencyKey(long createdBy, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayoutEntity p where p.id=:id")
    Optional<PayoutEntity> lockById(@Param("id") long id);
}
