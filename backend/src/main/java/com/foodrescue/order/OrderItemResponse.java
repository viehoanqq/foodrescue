package com.foodrescue.order;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long batchId,
        String title,
        String imageUrl,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {}
