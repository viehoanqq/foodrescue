package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.CategoryEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ISpringDataCategoryRepository extends JpaRepository<CategoryEntity, Long> {
    List<CategoryEntity> findByStoreIdAndArchivedAtIsNullOrderBySortOrderAscIdAsc(Long storeId);

    List<CategoryEntity> findByStoreIdInAndArchivedAtIsNullOrderBySortOrderAscIdAsc(
            List<Long> storeIds);

    List<CategoryEntity> findByStoreIdOrderBySortOrderAscIdAsc(Long storeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CategoryEntity> findForUpdateByIdAndStoreIdAndArchivedAtIsNull(Long id, Long storeId);

    boolean existsByStoreIdAndNameIgnoreCaseAndArchivedAtIsNull(Long storeId, String name);

    boolean existsByStoreIdAndNameIgnoreCaseAndIdNotAndArchivedAtIsNull(Long storeId, String name, Long id);

    boolean existsByIdAndStoreIdAndArchivedAtIsNull(Long id, Long storeId);

    boolean existsByIdAndStoreId(Long id, Long storeId);
}
