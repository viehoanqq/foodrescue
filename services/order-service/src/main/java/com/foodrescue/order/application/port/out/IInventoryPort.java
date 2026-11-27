package com.foodrescue.order.application.port.out;

import com.foodrescue.order.domain.model.StockReservation;
import com.foodrescue.order.domain.model.StockReservationCommand;
import java.util.UUID;
import java.util.List;

public interface IInventoryPort {
    Long requireSingleStore(List<Long> batchIds);
    StockReservation reserve(StockReservationCommand command, UUID correlationId);
    StockReservation find(UUID reservationId, UUID correlationId);
    StockReservation release(UUID reservationId, UUID correlationId);
}
