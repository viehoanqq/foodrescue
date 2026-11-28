package com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity;

import com.foodrescue.catalog.domain.model.enums.BatchStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** Persistence mapping for this service's own table; cross-service IDs stay scalar. */
@Entity
@Table(name = "rescue_batches")
public class RescueBatchEntity {
    public RescueBatchEntity() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "original_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal originalPrice;

    @Column(name = "rescue_price", nullable = false, precision = 12, scale = 0)
    private BigDecimal rescuePrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    @Column(name = "pickup_start", nullable = false)
    private LocalDateTime pickupStart;

    @Column(name = "pickup_end", nullable = false)
    private LocalDateTime pickupEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BatchStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }

    public BigDecimal getRescuePrice() { return rescuePrice; }
    public void setRescuePrice(BigDecimal rescuePrice) { this.rescuePrice = rescuePrice; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Integer getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(Integer remainingQuantity) { this.remainingQuantity = remainingQuantity; }

    public LocalDateTime getPickupStart() { return pickupStart; }
    public void setPickupStart(LocalDateTime pickupStart) { this.pickupStart = pickupStart; }

    public LocalDateTime getPickupEnd() { return pickupEnd; }
    public void setPickupEnd(LocalDateTime pickupEnd) { this.pickupEnd = pickupEnd; }

    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

    @Column(name = "aggregate_version", nullable = false)
    private Long aggregateVersion = 1L;

    public Long getAggregateVersion() { return aggregateVersion; }
    public void setAggregateVersion(Long aggregateVersion) { this.aggregateVersion = aggregateVersion; }
}
