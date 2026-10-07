package com.foodrescue.pickup;

import jakarta.validation.constraints.NotBlank;

public record HandoverRequest(
        @NotBlank(message = "Mã nhận hàng không được để trống")
        String pickupCode) {}
