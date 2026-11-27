package com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Dấu đã xử lý event của từng consumer để chống xử lý lặp. */
@Entity
@Table(name = "inbox_events")
@IdClass(InboxEventId.class)
public class InboxEventEntity {
    public InboxEventEntity() {}

    public InboxEventEntity(String consumerName, UUID eventId, Instant processedAt) {
        this.consumerName = consumerName;
        this.eventId = eventId;
        this.processedAt = processedAt;
    }

    @Id
    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
