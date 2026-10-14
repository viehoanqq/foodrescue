package com.foodrescue.pickup;

import com.foodrescue.order.OrderItemResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PickupLookupResponse(
        Long orderId,
        String orderCode,
        String pickupCode,
        String customerName,
        String customerPhone,
        int itemCount,
        BigDecimal totalAmount,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        List<OrderItemResponse> items,
        boolean early,
        boolean late) {}
