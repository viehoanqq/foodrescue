package com.foodrescue.dto.response;

import com.foodrescue.entity.enums.StoreStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 1 dòng trong danh sách cửa hàng của admin (GET /api/admin/stores). Có ảnh giấy phép và toạ độ để duyệt ngay trên danh sách. */
public record StoreSummaryResponse(
        Long id,
        String name,
        String address,
        String phone,
        BigDecimal latitude,
        BigDecimal longitude,
        String licenseImageUrl,
        String ownerName,
        String ownerEmail,
        StoreStatus status,
        String rejectReason,
        BigDecimal commissionRate,
        LocalDateTime createdAt) {
}
