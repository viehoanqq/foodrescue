package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.foodrescue.catalog.application.port.out.ICategoryRepository;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.domain.model.Category;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.CategoryEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCategoryRepository implements ICategoryRepository {
    private final ISpringDataCategoryRepository categories;
    private final ISpringDataRescueBatchRepository batches;

    public JpaCategoryRepository(ISpringDataCategoryRepository categories, ISpringDataRescueBatchRepository batches) {
        this.categories = categories;
        this.batches = batches;
    }

    @Override
    public List<Category> findByStore(Long storeId) {
        return categories.findByStoreIdAndArchivedAtIsNullOrderBySortOrderAscIdAsc(storeId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Category> findByStores(Collection<Long> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) return List.of();
        return categories.findByStoreIdInAndArchivedAtIsNullOrderBySortOrderAscIdAsc(List.copyOf(storeIds))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Category> findAllByStore(Long storeId) {
        return categories.findByStoreIdOrderBySortOrderAscIdAsc(storeId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Category> findForUpdate(Long storeId, Long id) {
        return categories.findForUpdateByIdAndStoreIdAndArchivedAtIsNull(id, storeId).map(this::toDomain);
    }

    @Override
    public boolean nameTaken(Long storeId, String name, Long excludeId) {
        return excludeId == null
                ? categories.existsByStoreIdAndNameIgnoreCaseAndArchivedAtIsNull(storeId, name)
                : categories.existsByStoreIdAndNameIgnoreCaseAndIdNotAndArchivedAtIsNull(storeId, name, excludeId);
    }

    @Override
    public boolean hasBatches(Long categoryId) {
        return batches.existsByCategoryId(categoryId);
    }

    @Override
    public Category save(Category category) {
        CategoryEntity entity = category.id() == null
                ? new CategoryEntity()
                : categories.findById(category.id()).orElseThrow(() ->
                        CatalogException.notFound("CATEGORY_NOT_FOUND", "Không tìm thấy danh mục"));
        entity.setStoreId(category.storeId());
        entity.setName(category.name());
        entity.setDescription(category.description());
        entity.setImageUrl(category.imageUrl());
        entity.setSortOrder(category.sortOrder());
        entity.setAggregateVersion(category.version());
        try {
            return toDomain(categories.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            throw CatalogException.conflict("CATEGORY_NAME_EXISTS",
                    "Tên danh mục đã tồn tại trong cửa hàng");
        }
    }

    @Override
    public void delete(Category category) {
        CategoryEntity entity = categories.findById(category.id()).orElseThrow(() ->
                CatalogException.notFound("CATEGORY_NOT_FOUND", "Không tìm thấy danh mục"));
        entity.setArchivedAt(java.time.Instant.now());
        entity.setAggregateVersion(category.version() + 1);
        categories.saveAndFlush(entity);
    }

    private Category toDomain(CategoryEntity e) {
        return new Category(e.getId(), e.getStoreId(), e.getName(), e.getDescription(), e.getImageUrl(),
                e.getSortOrder() == null ? 0 : e.getSortOrder(), e.getAggregateVersion(),
                e.getArchivedAt() == null);
    }
}
