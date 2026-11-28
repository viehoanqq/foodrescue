package com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Bản chiếu trạng thái cửa hàng nhận từ Store; store_id/owner_id là tham chiếu, không FK xuyên DB. */
@Entity
@Table(name = "store_status_projection")
public class StoreProjectionEntity {
    public StoreProjectionEntity() {}

    public StoreProjectionEntity(Long storeId, Long ownerId, String status, Long aggregateVersion) {
        this.storeId = storeId;
        this.ownerId = ownerId;
        this.status = status;
        this.aggregateVersion = aggregateVersion;
        this.updatedAt = Instant.now();
    }

    @Id
    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "aggregate_version", nullable = false)
    private Long aggregateVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getStoreId() { return storeId; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getAggregateVersion() { return aggregateVersion; }
    public void setAggregateVersion(Long aggregateVersion) { this.aggregateVersion = aggregateVersion; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
