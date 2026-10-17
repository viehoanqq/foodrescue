package com.foodrescue.repository;

import com.foodrescue.entity.Refund;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByOrderId(Long orderId);
}
