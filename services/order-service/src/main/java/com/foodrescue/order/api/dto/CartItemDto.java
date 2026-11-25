package com.foodrescue.order.api.dto;

import java.time.LocalDateTime;

public record CartItemDto(
        Long batchId,
        Integer quantity,
        LocalDateTime addedAt
) {}
