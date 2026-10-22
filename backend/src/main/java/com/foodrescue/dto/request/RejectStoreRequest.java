package com.foodrescue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body của POST /api/admin/stores/{id}/reject (lý do bắt buộc, lưu vào stores.reject_reason). */
public record RejectStoreRequest(
        @Schema(description = "Lý do từ chối", example = "Ảnh giấy phép VSATTP không rõ")
        @NotBlank(message = "Nhập lý do từ chối") @Size(max = 255) String reason) {
}
