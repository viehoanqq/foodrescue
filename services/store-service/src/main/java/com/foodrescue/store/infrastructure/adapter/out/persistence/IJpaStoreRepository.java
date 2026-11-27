package com.foodrescue.store.infrastructure.adapter.out.persistence;

import com.foodrescue.store.infrastructure.adapter.out.persistence.entity.StoreEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface IJpaStoreRepository extends JpaRepository<StoreEntity, Long> {
    Optional<StoreEntity> findByOwnerId(Long ownerId);
    Optional<StoreEntity> findByIdAndStatus(Long id, String status);
    Page<StoreEntity> findByStatus(String status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StoreEntity s where s.id = :id")
    Optional<StoreEntity> findLockedById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StoreEntity s where s.ownerId = :ownerId")
    Optional<StoreEntity> findLockedByOwnerId(Long ownerId);
    long countByStatus(String status);
}
