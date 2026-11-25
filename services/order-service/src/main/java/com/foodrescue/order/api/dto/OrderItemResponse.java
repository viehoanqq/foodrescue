package com.foodrescue.order.api.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long batchId,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {}
