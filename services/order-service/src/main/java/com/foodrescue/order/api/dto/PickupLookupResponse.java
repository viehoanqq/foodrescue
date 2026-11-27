package com.foodrescue.order.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PickupLookupResponse(
        Long orderId,
        String orderCode,
        String pickupCode,
        Long customerId,
        String status,
        String paymentStatus,
        Integer itemCount,
        BigDecimal totalAmount,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        boolean early,
        boolean late,
        boolean canHandover,
        List<OrderItemResponse> items
) {}
