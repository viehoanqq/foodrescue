package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.StoreProjectionEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ISpringDataStoreProjectionRepository extends JpaRepository<StoreProjectionEntity, Long> {
    Optional<StoreProjectionEntity> findByOwnerId(Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StoreProjectionEntity> findForUpdateByStoreId(Long storeId);
}
