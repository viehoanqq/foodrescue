package com.foodrescue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Body (tuỳ chọn) của POST /api/admin/stores/{id}/lock; lý do được ghi vào nhật ký thao tác. */
public record LockStoreRequest(
        @Schema(description = "Lý do khoá cửa hàng", example = "Vi phạm quy định an toàn thực phẩm")
        @Size(max = 255) String reason) {
}
