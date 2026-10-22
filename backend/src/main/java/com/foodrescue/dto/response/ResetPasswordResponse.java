package com.foodrescue.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Kết quả POST /api/admin/users/{id}/reset-password. Mật khẩu tạm chỉ hiện đúng 1 lần để admin đọc cho người dùng. */
public record ResetPasswordResponse(
        Long userId,
        @Schema(description = "Mật khẩu tạm (chỉ trả lần này)", example = "Tmp8x2kQ") String temporaryPassword,
        @Schema(description = "Luôn true: lần đăng nhập sau người dùng phải đổi mật khẩu") boolean mustChangePassword) {
}
