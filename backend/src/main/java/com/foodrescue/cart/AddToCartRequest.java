package com.foodrescue.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddToCartRequest(
        @NotNull(message = "Mã túi không được để trống")
        Long batchId,

        @Min(value = 1, message = "Số lượng tối thiểu là 1")
        @Max(value = 5, message = "Số lượng tối đa là 5")
        Integer quantity) {

    public int getResolvedQuantity() {
        return quantity != null ? quantity : 1;
    }
}
