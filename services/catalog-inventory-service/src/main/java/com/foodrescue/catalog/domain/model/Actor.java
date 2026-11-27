package com.foodrescue.catalog.domain.model;

/** Người gọi API đã được xác thực. storeId chỉ có với STORE_STAFF; chủ cửa hàng tra qua projection. */
public record Actor(Long userId, String role, Long storeId) {}
