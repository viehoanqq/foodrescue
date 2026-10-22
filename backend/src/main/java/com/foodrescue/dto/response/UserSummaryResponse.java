package com.foodrescue.dto.response;

import com.foodrescue.entity.enums.Role;
import com.foodrescue.entity.enums.UserStatus;
import java.time.LocalDateTime;

/** 1 người dùng trong màn quản lý của admin. Không bao giờ trả mật khẩu hay hash. */
public record UserSummaryResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        Role role,
        UserStatus status,
        Long storeId,
        String storeName,
        boolean mustChangePassword,
        LocalDateTime createdAt) {
}
