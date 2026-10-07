package com.foodrescue.cart;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CartResponse(
        Long storeId,
        String storeName,
        List<CartItemDto> items,
        BigDecimal totalAmount,
        int totalItems,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        boolean pickupConflict,
        boolean hasUnavailableItems) {}
