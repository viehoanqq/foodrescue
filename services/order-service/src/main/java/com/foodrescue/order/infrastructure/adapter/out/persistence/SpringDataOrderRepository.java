package com.foodrescue.order.infrastructure.adapter.out.persistence;

import com.foodrescue.order.domain.model.enums.OrderStatus;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("SELECT o FROM OrderEntity o WHERE o.customerId = :customerId "
            + "AND (:status IS NULL OR o.status = :status)")
    Page<OrderEntity> findCustomerOrders(@Param("customerId") Long customerId,
            @Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM OrderEntity o WHERE o.storeId = :storeId "
            + "AND (:status IS NULL OR o.status = :status) "
            + "AND (:from IS NULL OR o.createdAt >= :from) "
            + "AND (:until IS NULL OR o.createdAt < :until)")
    Page<OrderEntity> findStoreOrders(@Param("storeId") Long storeId,
            @Param("status") OrderStatus status, @Param("from") LocalDateTime from,
            @Param("until") LocalDateTime until, Pageable pageable);

    @Query("SELECT o FROM OrderEntity o WHERE (:status IS NULL OR o.status = :status) "
            + "AND (:code IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :code, '%')))")
    Page<OrderEntity> findAdminOrders(@Param("status") OrderStatus status,
            @Param("code") String code, Pageable pageable);

    @Query("SELECT o.id FROM OrderEntity o WHERE o.status = :status AND o.paymentExpiresAt < :now")
    List<Long> findExpiredPendingOrderIds(@Param("status") OrderStatus status, @Param("now") LocalDateTime now);

    @Query("SELECT o.id FROM OrderEntity o WHERE o.status = :status AND o.pickupEnd < :deadline")
    List<Long> findNoShowOrderIds(@Param("status") OrderStatus status, @Param("deadline") LocalDateTime deadline);
}
