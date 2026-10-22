package com.foodrescue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body của POST /api/store/batches/{id}/cancel. Chỉ STORE_OWNER (BR-24). */
public record CancelBatchRequest(
        @Schema(description = "Lý do huỷ túi (ghi vào nhật ký và lý do hoàn tiền BATCH_CANCELLED)", example = "Hết nguyên liệu")
        @NotBlank(message = "Nhập lý do huỷ túi") @Size(max = 255) String reason) {
}
