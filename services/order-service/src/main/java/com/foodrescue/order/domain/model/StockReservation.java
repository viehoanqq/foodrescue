package com.foodrescue.order.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StockReservation(UUID reservationId, String status, List<Item> items,
                               LocalDateTime pickupStart, LocalDateTime pickupEnd) {
    public record Item(Long batchId, int quantity, BigDecimal unitPrice) {}
}
