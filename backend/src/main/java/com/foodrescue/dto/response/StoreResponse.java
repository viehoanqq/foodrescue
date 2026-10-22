package com.foodrescue.dto.response;

import com.foodrescue.entity.enums.StoreStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Hồ sơ đầy đủ của 1 cửa hàng (đăng ký, hồ sơ cửa hàng, kết quả duyệt / khoá của admin). Trường rỗng trả null. */
public record StoreResponse(
        Long id,
        Long ownerId,
        String ownerName,
        String ownerEmail,
        String name,
        String description,
        String phone,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl,
        String licenseImageUrl,
        LocalTime openTime,
        LocalTime closeTime,
        StoreStatus status,
        @Schema(description = "Lý do từ chối (chỉ có khi status = REJECTED)") String rejectReason,
        @Schema(description = "Tỉ lệ phí nền tảng (%)", example = "15.00") BigDecimal commissionRate,
        String bankName,
        String bankAccountNo,
        String bankAccountName,
        LocalDateTime createdAt) {
}
