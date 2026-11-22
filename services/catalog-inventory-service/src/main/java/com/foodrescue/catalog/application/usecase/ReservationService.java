package com.foodrescue.catalog.application.usecase;

import com.foodrescue.catalog.application.port.out.ReservationPort;
import com.foodrescue.catalog.domain.model.ReservationRequest;
import com.foodrescue.catalog.domain.model.ReservationResult;
import java.util.UUID;

public class ReservationService {
    private final ReservationPort reservations;

    public ReservationService(ReservationPort reservations) { this.reservations = reservations; }

    public ReservationResult reserve(ReservationRequest request) { return reservations.reserve(request); }
    public ReservationResult release(UUID id) { return reservations.release(id); }
    public ReservationResult load(UUID id) { return reservations.load(id); }
}
