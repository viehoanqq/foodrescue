package com.foodrescue.order.api.dto;

import java.util.List;

public record CartResponse(
        Long customerId,
        int totalItems,
        List<CartItemDto> items
) {}
