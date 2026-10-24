package com.foodrescue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Body của PUT /api/admin/stores/{id}/commission. Chỉ áp cho đơn đặt SAU khi đổi (đơn cũ giữ tỉ lệ đã copy). */
public record UpdateStoreCommissionRequest(
        @Schema(description = "Tỉ lệ phí nền tảng (%), 0 đến 50", example = "12.50")
        @NotNull(message = "Nhập tỉ lệ phí")
        @DecimalMin(value = "0", message = "Tỉ lệ phí từ 0 đến 50")
        @DecimalMax(value = "50", message = "Tỉ lệ phí từ 0 đến 50") BigDecimal commissionRate) {
}
