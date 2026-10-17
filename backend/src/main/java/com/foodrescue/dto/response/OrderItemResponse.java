package com.foodrescue.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long batchId,
        String title,
        String imageUrl,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {}
