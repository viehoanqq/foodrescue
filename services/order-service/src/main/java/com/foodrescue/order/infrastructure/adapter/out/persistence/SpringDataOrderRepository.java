package com.foodrescue.order.infrastructure.adapter.out.persistence;

import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, Long> {
    Optional<OrderEntity> findByOrderCode(String orderCode);
    Optional<OrderEntity> findByPickupCode(String pickupCode);
    Optional<OrderEntity> findByReservationId(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderEntity> findLockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.pickupCode = :pickupCode")
    Optional<OrderEntity> findLockedByPickupCode(@Param("pickupCode") String pickupCode);

    List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<OrderEntity> findByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId, String status);

    List<OrderEntity> findByStoreIdOrderByCreatedAtDesc(Long storeId);
    List<OrderEntity> findByStoreIdAndStatusOrderByCreatedAtDesc(Long storeId, String status);

    List<OrderEntity> findByStatusOrderByCreatedAtDesc(String status);

    @Query("SELECT o.id FROM OrderEntity o WHERE o.status = :status AND o.paymentExpiresAt < :now")
    List<Long> findExpiredPendingOrderIds(@Param("status") String status, @Param("now") LocalDateTime now);

    @Query("SELECT o.id FROM OrderEntity o WHERE o.status = :status AND o.pickupEnd < :deadline")
    List<Long> findNoShowOrderIds(@Param("status") String status, @Param("deadline") LocalDateTime deadline);
}
