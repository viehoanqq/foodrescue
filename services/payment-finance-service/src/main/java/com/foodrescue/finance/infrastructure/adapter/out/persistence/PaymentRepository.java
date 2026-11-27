package com.foodrescue.finance.infrastructure.adapter.out.persistence;

import com.foodrescue.finance.infrastructure.adapter.out.persistence.entity.*;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByCustomerIdAndIdempotencyKey(
            long customerId, String idempotencyKey);

    Optional<PaymentEntity> findByTxnRef(String txnRef);

    Optional<PaymentEntity> findFirstByOrderIdOrderByIdDesc(long orderId);

    Optional<PaymentEntity> findByOrderIdAndStatus(long orderId, String status);

    boolean existsByOrderIdAndStatusIn(long orderId, Collection<String> statuses);
}
