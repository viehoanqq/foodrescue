package com.foodrescue.dto.response;

import java.math.BigDecimal;

public record PaymentResponse(
        String paymentUrl,
        String txnRef,
        BigDecimal amount,
        String orderCode) {}
