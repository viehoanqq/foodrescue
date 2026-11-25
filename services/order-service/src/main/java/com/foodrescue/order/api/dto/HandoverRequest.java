package com.foodrescue.order.api.dto;

import jakarta.validation.constraints.NotBlank;

public record HandoverRequest(
        @NotBlank(message = "pickupCode không được để trống")
        String pickupCode
) {}
