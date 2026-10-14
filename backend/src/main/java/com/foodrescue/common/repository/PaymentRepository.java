package com.foodrescue.common.repository;

import com.foodrescue.common.entity.Payment;
import com.foodrescue.common.enums.PaymentTxnStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTxnRef(String txnRef);

    List<Payment> findByOrderId(Long orderId);

    Optional<Payment> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(Long orderId, PaymentTxnStatus status);
}
