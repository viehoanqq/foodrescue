package com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

/** Persistence mapping for this service's own table; cross-service IDs stay scalar. */
@Entity
@Table(name = "stock_movements")
public class StockMovementEntity {
    public StockMovementEntity() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "change_qty", nullable = false)
    private Integer changeQty;

    @Column(name = "reason", nullable = false, length = 20)
    private String reason;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "remaining_after", nullable = false)
    private Integer remainingAfter;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public Integer getChangeQty() { return changeQty; }
    public void setChangeQty(Integer changeQty) { this.changeQty = changeQty; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }

    public Integer getRemainingAfter() { return remainingAfter; }
    public void setRemainingAfter(Integer remainingAfter) { this.remainingAfter = remainingAfter; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
