package com.foodrescue.finance.infrastructure.adapter.out.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PayoutItemEntityId implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long payoutId;
    private Long orderId;

    public PayoutItemEntityId() {}

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PayoutItemEntityId that)) return false;
        return Objects.equals(payoutId, that.payoutId) && Objects.equals(orderId, that.orderId);
    }

    @Override public int hashCode() {
        return Objects.hash(payoutId, orderId);
    }
}
