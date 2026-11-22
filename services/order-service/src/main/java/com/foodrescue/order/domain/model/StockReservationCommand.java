package com.foodrescue.order.domain.model;

import java.util.List;
import java.util.UUID;

public record StockReservationCommand(
        UUID reservationId, Long orderId, Long storeId, Long actorId, List<Item> items) {
    public StockReservationCommand {
        if (reservationId == null || orderId == null || orderId <= 0 || storeId == null || storeId <= 0
                || actorId == null || actorId <= 0 || items == null || items.isEmpty() || items.contains(null)) {
            throw new IllegalArgumentException("Invalid reservation command");
        }
        items = List.copyOf(items);
    }

    public record Item(Long batchId, int quantity) {
        public Item {
            if (batchId == null || batchId <= 0 || quantity < 1 || quantity > 5) {
                throw new IllegalArgumentException("Invalid reservation item");
            }
        }
    }
}
