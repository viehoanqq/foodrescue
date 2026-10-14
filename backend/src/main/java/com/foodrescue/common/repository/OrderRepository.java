package com.foodrescue.common.repository;

import com.foodrescue.common.entity.Order;
import com.foodrescue.common.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    Optional<Order> findByPickupCode(String pickupCode);

    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Order> findByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId, OrderStatus status);

    List<Order> findByStoreIdOrderByCreatedAtDesc(Long storeId);

    List<Order> findByStoreIdAndStatusOrderByCreatedAtDesc(Long storeId, OrderStatus status);

    List<Order> findByStatusAndPaymentExpiresAtBefore(OrderStatus status, LocalDateTime time);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.pickupEnd < :cutoff")
    List<Order> findConfirmedOrdersPastPickupGrace(
            @Param("status") OrderStatus status,
            @Param("cutoff") LocalDateTime cutoff);
}
