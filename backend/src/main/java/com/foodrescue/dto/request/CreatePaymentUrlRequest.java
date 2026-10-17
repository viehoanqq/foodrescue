package com.foodrescue.dto.request;

import jakarta.validation.constraints.NotNull;

public record CreatePaymentUrlRequest(
        @NotNull(message = "Mã đơn hàng không được để trống")
        Long orderId) {}
