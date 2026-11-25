package com.foodrescue.order.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PickupLookupResponse(
        Long orderId,
        String orderCode,
        String pickupCode,
        Long customerId,
        Integer itemCount,
        BigDecimal totalAmount,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        boolean early,
        boolean late,
        List<OrderItemResponse> items
) {}
