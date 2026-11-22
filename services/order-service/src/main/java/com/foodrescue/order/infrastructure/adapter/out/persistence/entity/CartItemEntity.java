package com.foodrescue.order.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

/** Persistence mapping for this service's own table; cross-service IDs stay scalar. */
@Entity
@Table(name = "cart_items")
@IdClass(CartItemEntityId.class)
public class CartItemEntity {
    public CartItemEntity() {}

    @Id
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Id
    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
}
