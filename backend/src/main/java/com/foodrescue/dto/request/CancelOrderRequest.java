package com.foodrescue.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CancelOrderRequest(
        @NotBlank(message = "Lý do huỷ đơn không được để trống")
        String reason) {}
