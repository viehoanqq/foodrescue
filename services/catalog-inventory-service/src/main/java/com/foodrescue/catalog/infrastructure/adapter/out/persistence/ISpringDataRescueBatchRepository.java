package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.RescueBatchEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ISpringDataRescueBatchRepository extends JpaRepository<RescueBatchEntity, Long> {
    boolean existsByCategoryId(Long categoryId);

    List<RescueBatchEntity> findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long storeId);

    Page<RescueBatchEntity> findByStoreIdOrderByCreatedAtDesc(Long storeId, Pageable pageable);

    Page<RescueBatchEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RescueBatchEntity> findForUpdateByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);

    Optional<RescueBatchEntity> findByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RescueBatchEntity> findForUpdateByIdAndDeletedAtIsNull(Long id);

    boolean existsByIdAndStoreIdAndDeletedAtIsNull(Long id, Long storeId);

}
