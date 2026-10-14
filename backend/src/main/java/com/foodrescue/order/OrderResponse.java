package com.foodrescue.order;

import com.foodrescue.common.enums.OrderStatus;
import com.foodrescue.common.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderCode,
        Long storeId,
        String storeName,
        String storeAddress,
        String storePhone,
        String customerName,
        String customerPhone,
        int itemCount,
        BigDecimal totalAmount,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal storeEarning,
        LocalDateTime pickupStart,
        LocalDateTime pickupEnd,
        String pickupCode,
        OrderStatus status,
        PaymentStatus paymentStatus,
        LocalDateTime paymentExpiresAt,
        LocalDateTime completedAt,
        String cancelReason,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt,
        List<OrderItemResponse> items,
        boolean canCancel) {}
