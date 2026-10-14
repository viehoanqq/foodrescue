package com.foodrescue.cart;

import com.foodrescue.common.enums.BatchStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CartItemDto(
        Long batchId,
        int quantity,
        String title,
        BigDecimal originalPrice,
        BigDecimal rescuePrice,
        String imageUrl,
        Long storeId,
        String storeName,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        int remainingQuantity,
        BatchStatus status,
        BigDecimal subtotal,
        boolean outOfStock) {}
