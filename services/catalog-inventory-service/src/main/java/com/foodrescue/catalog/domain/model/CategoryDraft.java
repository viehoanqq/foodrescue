package com.foodrescue.catalog.domain.model;

/** Dữ liệu tạo/sửa danh mục do người dùng gửi lên. */
public record CategoryDraft(String name, String description, String imageUrl, Integer sortOrder) {}
