package com.foodrescue.order.api.dto;

import jakarta.validation.Valid;
import java.util.List;

public record CreateOrderRequest(
        Long storeId,
        @Valid
        List<CreateOrderItemRequest> items
) {}
