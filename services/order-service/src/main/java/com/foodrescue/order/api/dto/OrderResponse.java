package com.foodrescue.order.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        Long id,
        String orderCode,
        Long customerId,
        Long storeId,
        Integer itemCount,
        BigDecimal totalAmount,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal storeEarning,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        String pickupCode,
        String status,
        String paymentStatus,
        String cancelReason,
        LocalDateTime paymentExpiresAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        UUID reservationId,
        Long aggregateVersion,
        List<OrderItemResponse> items
) {}
