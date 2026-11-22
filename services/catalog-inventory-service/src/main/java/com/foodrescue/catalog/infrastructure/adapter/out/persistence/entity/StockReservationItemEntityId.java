package com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class StockReservationItemEntityId implements Serializable {
    private static final long serialVersionUID = 1L;
    private UUID reservationId;
    private Long batchId;

    public StockReservationItemEntityId() {}

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StockReservationItemEntityId that)) return false;
        return Objects.equals(reservationId, that.reservationId) && Objects.equals(batchId, that.batchId);
    }

    @Override public int hashCode() {
        return Objects.hash(reservationId, batchId);
    }
}
