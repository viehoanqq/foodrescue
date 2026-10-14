package com.foodrescue.batch;

import com.foodrescue.common.enums.BatchStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record BatchDetailResponse(
        Long id,
        String title,
        String description,
        String imageUrl,
        BigDecimal originalPrice,
        BigDecimal rescuePrice,
        int quantity,
        int remainingQuantity,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        BatchStatus status,
        Long storeId,
        String storeName,
        String storeAddress,
        String storePhone,
        LocalTime openTime,
        LocalTime closeTime,
        BigDecimal latitude,
        BigDecimal longitude,
        Double averageRating,
        long reviewsCount,
        List<BatchReviewDto> reviews
) {
    public record BatchReviewDto(
            Long id,
            String customerName,
            int rating,
            String comment,
            LocalDateTime createdAt
    ) {}
}
