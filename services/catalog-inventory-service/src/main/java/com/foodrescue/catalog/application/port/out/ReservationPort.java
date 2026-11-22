package com.foodrescue.catalog.application.port.out;

import com.foodrescue.catalog.domain.model.ReservationRequest;
import com.foodrescue.catalog.domain.model.ReservationResult;
import java.util.UUID;

public interface ReservationPort {
    ReservationResult reserve(ReservationRequest request);
    ReservationResult release(UUID reservationId);
    ReservationResult load(UUID reservationId);
}
