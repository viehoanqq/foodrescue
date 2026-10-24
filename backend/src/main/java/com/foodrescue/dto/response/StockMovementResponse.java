package com.foodrescue.dto.response;

import com.foodrescue.entity.enums.StockReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 1 dòng sổ kho của túi (GET /api/store/batches/{id}/stock-movements trả mảng, cũ đến mới). */
public record StockMovementResponse(
        Long id,
        StockReason reason,
        @Schema(description = "Dương = tăng, âm = giảm", example = "-3") int changeQty,
        @Schema(description = "Số còn lại sau thay đổi", example = "8") int remainingAfter,
        @Schema(description = "Mã đơn (chỉ có với ORDERED / RETURNED)") String orderCode,
        @Schema(description = "Người thực hiện; null = hệ thống") String actorName,
        String note,
        LocalDateTime createdAt) {
}
