package com.foodrescue.order.application.usecase;

import com.foodrescue.order.domain.model.StockReservation;
import com.foodrescue.order.domain.model.StockReservationCommand;
import java.util.UUID;
import com.foodrescue.order.application.port.out.IInventoryPort;

/** First integration slice; order persistence and payment saga are added separately. */
public class StockReservationUseCase {
    private final IInventoryPort inventory;

    public StockReservationUseCase(IInventoryPort inventory) { this.inventory = inventory; }

    public StockReservation reserve(StockReservationCommand command, UUID correlationId) {
        return inventory.reserve(command, correlationId);
    }

    public StockReservation find(UUID reservationId, UUID correlationId) {
        return inventory.find(reservationId, correlationId);
    }

    public StockReservation release(UUID reservationId, UUID correlationId) {
        return inventory.release(reservationId, correlationId);
    }
}
