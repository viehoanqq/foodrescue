package com.foodrescue.order.infrastructure.adapter.out.persistence;

import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, Long> {
    Optional<OrderEntity> findByOrderCode(String orderCode);
    Optional<OrderEntity> findByPickupCode(String pickupCode);
    Optional<OrderEntity> findByReservationId(UUID reservationId);

    List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<OrderEntity> findByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId, String status);

    List<OrderEntity> findByStoreIdOrderByCreatedAtDesc(Long storeId);
    List<OrderEntity> findByStoreIdAndStatusOrderByCreatedAtDesc(Long storeId, String status);

    @Query("SELECT o FROM OrderEntity o WHERE o.status = :status AND o.paymentExpiresAt < :now")
    List<OrderEntity> findExpiredPendingOrders(@Param("status") String status, @Param("now") LocalDateTime now);

    @Query("SELECT o FROM OrderEntity o WHERE o.status = :status AND o.pickupEnd < :deadline")
    List<OrderEntity> findNoShowOrders(@Param("status") String status, @Param("deadline") LocalDateTime deadline);
}
