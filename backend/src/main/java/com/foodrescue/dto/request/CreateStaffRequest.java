package com.foodrescue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body của POST /api/store/staff (chỉ STORE_OWNER). Tạo STORE_STAFF thuộc cửa hàng của chủ, must_change_password = TRUE. */
public record CreateStaffRequest(
        @Schema(description = "Email đăng nhập (duy nhất)", example = "staff.moi@gmail.com")
        @NotBlank(message = "Nhập email") @Email(message = "Email không hợp lệ") @Size(max = 100) String email,
        @Schema(example = "Đỗ Minh Khoa") @NotBlank(message = "Nhập họ tên") @Size(max = 100) String fullName,
        @Schema(example = "0933000009") @NotBlank(message = "Nhập số điện thoại") @Size(max = 20) String phone,
        @Schema(description = "Mật khẩu tạm, tối thiểu 6 ký tự; nhân viên phải đổi ở lần đăng nhập đầu", example = "123456")
        @NotBlank(message = "Nhập mật khẩu tạm") @Size(min = 6, max = 72, message = "Mật khẩu tối thiểu 6 ký tự") String temporaryPassword) {
}
