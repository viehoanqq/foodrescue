package com.foodrescue.finance.infrastructure.adapter.out.persistence;

import com.foodrescue.finance.infrastructure.adapter.out.persistence.entity.*;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface RefundRepository extends JpaRepository<RefundEntity, Long> {

    boolean existsByOrderId(long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefundEntity r where r.id=:id")
    Optional<RefundEntity> lockById(@Param("id") long id);
}
