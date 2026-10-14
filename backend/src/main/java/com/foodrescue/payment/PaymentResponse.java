package com.foodrescue.payment;

import java.math.BigDecimal;

public record PaymentResponse(
        String paymentUrl,
        String txnRef,
        BigDecimal amount,
        String orderCode) {}
