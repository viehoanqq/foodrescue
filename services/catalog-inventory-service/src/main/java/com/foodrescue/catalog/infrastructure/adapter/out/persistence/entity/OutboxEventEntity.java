package com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Dòng outbox; payload là cả envelope JSON, OutboxPublisher gửi nguyên văn lên RabbitMQ. */
@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {
    public OutboxEventEntity() {}

    public OutboxEventEntity(UUID eventId, String aggregateType, String aggregateId, long aggregateVersion,
                             String eventType, int schemaVersion, JsonNode payload, Instant occurredAt) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 80)
    private String aggregateId;

    @Column(name = "aggregate_version", nullable = false)
    private Long aggregateVersion;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private JsonNode payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public JsonNode getPayload() { return payload; }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
        this.attempts++;
    }
}
