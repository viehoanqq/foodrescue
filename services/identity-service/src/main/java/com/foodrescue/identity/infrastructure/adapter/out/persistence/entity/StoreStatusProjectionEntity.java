package com.foodrescue.identity.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_status_projection")
public class StoreStatusProjectionEntity {
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
    private LocalDateTime updatedAt;

    protected StoreStatusProjectionEntity() {}
}
