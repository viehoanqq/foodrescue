package com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Persistence mapping for this service's own table; cross-service IDs stay scalar. */
@Entity
@Table(name = "categories")
public class CategoryEntity {
    public CategoryEntity() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "aggregate_version", nullable = false)
    private Long aggregateVersion = 1L;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getAggregateVersion() { return aggregateVersion; }
    public void setAggregateVersion(Long aggregateVersion) { this.aggregateVersion = aggregateVersion; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
