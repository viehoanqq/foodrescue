package com.foodrescue.catalog.domain.model;

/** Danh mục thuộc đúng một cửa hàng. version là aggregate_version (bắt đầu từ 1). */
public record Category(Long id, Long storeId, String name, String description,
                       String imageUrl, int sortOrder, long version, boolean active) {
    public Category(Long id, Long storeId, String name, String description,
                    String imageUrl, int sortOrder, long version) {
        this(id, storeId, name, description, imageUrl, sortOrder, version, true);
    }

    public Category withDraft(CategoryDraft draft) {
        return new Category(id, storeId, draft.name(), draft.description(), draft.imageUrl(),
                draft.sortOrder() == null ? 0 : draft.sortOrder(), version + 1, active);
    }
}
