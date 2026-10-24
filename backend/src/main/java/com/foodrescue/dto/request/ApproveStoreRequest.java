package com.foodrescue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/** Body (tuỳ chọn) của POST /api/admin/stores/{id}/approve. Bỏ trống thì giữ tỉ lệ phí đang có (mặc định 15%). */
public record ApproveStoreRequest(
        @Schema(description = "Tỉ lệ phí nền tảng (%), 0 đến 50", example = "10.00")
        @DecimalMin(value = "0", message = "Tỉ lệ phí từ 0 đến 50")
        @DecimalMax(value = "50", message = "Tỉ lệ phí từ 0 đến 50") BigDecimal commissionRate) {
}
