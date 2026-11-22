package com.foodrescue.order.infrastructure.adapter.out.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class CartItemEntityId implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long customerId;
    private Long batchId;

    public CartItemEntityId() {}

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CartItemEntityId that)) return false;
        return Objects.equals(customerId, that.customerId) && Objects.equals(batchId, that.batchId);
    }

    @Override public int hashCode() {
        return Objects.hash(customerId, batchId);
    }
}
