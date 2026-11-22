package com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

/** Persistence mapping for this service's own table; cross-service IDs stay scalar. */
@Entity
@Table(name = "stock_reservation_items")
@IdClass(StockReservationItemEntityId.class)
public class StockReservationItemEntity {
    public StockReservationItemEntity() {}

    @Id
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Id
    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal unitPrice;

    public UUID getReservationId() { return reservationId; }
    public void setReservationId(UUID reservationId) { this.reservationId = reservationId; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
