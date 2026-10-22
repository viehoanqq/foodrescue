package com.foodrescue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body của PATCH /api/store/batches/{id}/quantity. delta dương = thêm túi (ADDED), âm = bớt túi (REMOVED).
 * Cộng/trừ cả quantity và remainingQuantity (BR-18); bớt quá số còn lại thì bị chặn (STOCK_NOT_ENOUGH). delta khác 0.
 */
public record AdjustBatchQuantityRequest(
        @Schema(description = "Số túi thêm (+) hoặc bớt (-), khác 0", example = "2")
        @NotNull(message = "Nhập số lượng thay đổi")
        @Min(value = -999, message = "Số lượng thay đổi từ -999 đến 999")
        @Max(value = 999, message = "Số lượng thay đổi từ -999 đến 999") Integer delta,
        @Schema(description = "Ghi chú vào sổ kho", example = "Khách mua trực tiếp tại quầy")
        @Size(max = 255) String note) {
}
