package com.foodrescue.payment;

import java.math.BigDecimal;

public record PaymentVerifyResponse(
        boolean success,
        Long orderId,
        String orderCode,
        BigDecimal amount,
        String message) {}
