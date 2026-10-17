package com.foodrescue.security;

import com.foodrescue.entity.enums.Role;

/**
 * Người đang đăng nhập (đọc lại từ CSDL ở mỗi request).
 * storeId: cửa hàng của chủ (STORE_OWNER) hoặc của nhân viên (STORE_STAFF); vai trò khác là null.
 */
public record AuthUser(
        Long id,
        String email,
        String fullName,
        Role role,
        Long storeId,
        boolean mustChangePassword) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isStoreSide() {
        return role == Role.STORE_OWNER || role == Role.STORE_STAFF;
    }
}
