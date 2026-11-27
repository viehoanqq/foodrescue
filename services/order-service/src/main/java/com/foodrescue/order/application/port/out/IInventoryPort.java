package com.foodrescue.order.application.port.out;

import com.foodrescue.order.domain.model.StockReservation;
import com.foodrescue.order.domain.model.StockReservationCommand;
import java.util.UUID;

public interface IInventoryPort {
    StockReservation reserve(StockReservationCommand command, UUID correlationId);
    StockReservation find(UUID reservationId, UUID correlationId);
    StockReservation release(UUID reservationId, UUID correlationId);
    BatchInfo getBatch(Long batchId, UUID correlationId);

    record BatchInfo(Long id, Long storeId, String status) {}
}
