package com.foodrescue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phần "data" (JSON) của POST /api/store/batches (multipart; phần file tuỳ chọn tên "image"). Chỉ STORE_OWNER.
 * Kiểm ở service: giá giải cứu &lt;= 60% giá gốc (BR-03, PRICE_DISCOUNT_TOO_LOW);
 * pickupEnd &gt; pickupStart và chưa trôi qua (BR-04, PICKUP_TIME_INVALID).
 */
public record CreateBatchRequest(
        @Schema(description = "Danh mục", example = "4") @NotNull(message = "Chọn danh mục") Long categoryId,
        @Schema(description = "Tên túi", example = "Túi Rau Củ Cuối Ngày")
        @NotBlank(message = "Nhập tên túi") @Size(max = 150) String title,
        @Schema(description = "Mô tả nội dung túi", example = "Rau củ tươi trong ngày, lưu ý bảo quản tủ mát.")
        @Size(max = 2000) String description,
        @Schema(description = "Giá gốc (VNĐ, số nguyên)", example = "80000")
        @NotNull(message = "Nhập giá gốc") @Positive(message = "Giá gốc phải lớn hơn 0") BigDecimal originalPrice,
        @Schema(description = "Giá giải cứu (VNĐ), tối đa 60% giá gốc", example = "30000")
        @NotNull(message = "Nhập giá giải cứu") @Positive(message = "Giá giải cứu phải lớn hơn 0") BigDecimal rescuePrice,
        @Schema(description = "Tổng số túi đưa lên", example = "10")
        @NotNull(message = "Nhập số lượng") @Min(value = 1, message = "Số lượng tối thiểu 1")
        @Max(value = 999, message = "Số lượng tối đa 999") Integer quantity,
        @Schema(description = "Bắt đầu lấy hàng (giờ Việt Nam)", example = "2026-12-31T20:30:00")
        @NotNull(message = "Chọn giờ bắt đầu lấy hàng") LocalDateTime pickupStart,
        @Schema(description = "Kết thúc lấy hàng (giờ Việt Nam)", example = "2026-12-31T21:30:00")
        @NotNull(message = "Chọn giờ kết thúc lấy hàng") LocalDateTime pickupEnd) {
}
