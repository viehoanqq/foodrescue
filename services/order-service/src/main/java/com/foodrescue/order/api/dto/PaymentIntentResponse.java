package com.foodrescue.order.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentIntentResponse(
        Long orderId,
        String orderCode,
        Long customerId,
        Long storeId,
        BigDecimal totalAmount,
        String status,
        String paymentStatus,
        LocalDateTime paymentExpiresAt
) {}
