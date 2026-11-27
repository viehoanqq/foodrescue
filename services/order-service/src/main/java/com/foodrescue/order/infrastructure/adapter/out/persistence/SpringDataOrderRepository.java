package com.foodrescue.order.infrastructure.adapter.out.persistence;

import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, Long> {
    Optional<OrderEntity> findByOrderCode(String orderCode);
    Optional<OrderEntity> findByPickupCode(String pickupCode);
    Optional<OrderEntity> findByReservationId(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.pickupCode = :pickupCode")
    Optional<OrderEntity> findByPickupCodeForUpdate(@Param("pickupCode") String pickupCode);

    @Modifying
    @Query("UPDATE OrderEntity o SET o.refundRequested = true WHERE o.id = :id AND o.refundRequested = false")
    int claimRefundRequest(@Param("id") Long id);

    List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<OrderEntity> findByCustomerIdAndStatusOrderByCreatedAtDesc(Long customerId, String status);

    List<OrderEntity> findByStoreIdOrderByCreatedAtDesc(Long storeId);
    List<OrderEntity> findByStoreIdAndStatusOrderByCreatedAtDesc(Long storeId, String status);

    List<OrderEntity> findByStatusOrderByCreatedAtDesc(String status);

    @Query("""
        SELECT o FROM OrderEntity o
        WHERE o.customerId = :customerId
          AND (:status IS NULL OR o.status = :status)
          AND (:fromDate IS NULL OR o.createdAt >= :fromDate)
          AND (:toDate IS NULL OR o.createdAt < :toDate)
          AND (:code IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :code, '%'))
               OR LOWER(o.pickupCode) LIKE LOWER(CONCAT('%', :code, '%')))
        ORDER BY o.createdAt DESC
        """)
    Page<OrderEntity> searchCustomerOrders(
            @Param("customerId") Long customerId,
            @Param("status") String status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("code") String code,
            Pageable pageable);

    @Query("""
        SELECT o FROM OrderEntity o
        WHERE o.storeId = :storeId
          AND (:status IS NULL OR o.status = :status)
          AND (:fromDate IS NULL OR o.createdAt >= :fromDate)
          AND (:toDate IS NULL OR o.createdAt < :toDate)
          AND (:code IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :code, '%'))
               OR LOWER(o.pickupCode) LIKE LOWER(CONCAT('%', :code, '%')))
        ORDER BY o.createdAt DESC
        """)
    Page<OrderEntity> searchStoreOrders(
            @Param("storeId") Long storeId,
            @Param("status") String status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("code") String code,
            Pageable pageable);

    @Query("""
        SELECT o FROM OrderEntity o
        WHERE (:storeId IS NULL OR o.storeId = :storeId)
          AND (:status IS NULL OR o.status = :status)
          AND (:fromDate IS NULL OR o.createdAt >= :fromDate)
          AND (:toDate IS NULL OR o.createdAt < :toDate)
          AND (:code IS NULL OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :code, '%'))
               OR LOWER(o.pickupCode) LIKE LOWER(CONCAT('%', :code, '%')))
        ORDER BY o.createdAt DESC
        """)
    Page<OrderEntity> searchAdminOrders(
            @Param("storeId") Long storeId,
            @Param("status") String status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("code") String code,
            Pageable pageable);

    @Query("SELECT o FROM OrderEntity o WHERE o.status = :status AND o.paymentExpiresAt < :now")
    List<OrderEntity> findExpiredPendingOrders(@Param("status") String status, @Param("now") LocalDateTime now);

    @Query("SELECT o FROM OrderEntity o WHERE o.status = :status AND o.pickupEnd < :deadline")
    List<OrderEntity> findNoShowOrders(@Param("status") String status, @Param("deadline") LocalDateTime deadline);
}
