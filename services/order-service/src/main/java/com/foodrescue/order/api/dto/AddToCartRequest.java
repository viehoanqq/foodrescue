package com.foodrescue.order.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddToCartRequest(
        @NotNull(message = "batchId không được để trống")
        Long batchId,
        @NotNull(message = "quantity không được để trống")
        @Min(value = 1, message = "Số lượng tối thiểu là 1")
        @Max(value = 5, message = "Số lượng tối đa là 5")
        Integer quantity
) {}
