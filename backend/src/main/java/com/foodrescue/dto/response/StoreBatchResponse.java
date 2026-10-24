package com.foodrescue.dto.response;

import com.foodrescue.entity.enums.BatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 1 túi trong màn quản lý của cửa hàng (khác BatchCardResponse của khách: không có khoảng cách, có số đã bán). */
public record StoreBatchResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String title,
        String description,
        String imageUrl,
        BigDecimal originalPrice,
        BigDecimal rescuePrice,
        @Schema(description = "Phần trăm giảm, làm tròn", example = "63") int discountPercent,
        @Schema(description = "Tổng số túi đã đăng = đã bán + còn lại (BR-18)") int quantity,
        int remainingQuantity,
        @Schema(description = "quantity - remainingQuantity") int soldQuantity,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        BatchStatus status,
        @Schema(description = "true: túi đã có đơn, không sửa được giá và khung giờ") boolean hasOrders,
        LocalDateTime createdAt) {
}
