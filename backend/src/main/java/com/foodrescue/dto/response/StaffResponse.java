package com.foodrescue.dto.response;

import com.foodrescue.entity.enums.UserStatus;
import java.time.LocalDateTime;

/** 1 nhân viên quầy của cửa hàng. */
public record StaffResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        UserStatus status,
        boolean mustChangePassword,
        LocalDateTime createdAt) {
}
