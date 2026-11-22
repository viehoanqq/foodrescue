package com.foodrescue.buildingblocks.events;

import java.time.Instant;
import java.util.UUID;

/** Wire envelope shared by producers and consumers; payload remains service-owned. */
public record EventEnvelope<T>(UUID eventId, String eventType, int schemaVersion,
                               String aggregateType, String aggregateId, long aggregateVersion,
                               Instant occurredAt, UUID correlationId, UUID causationId,
                               Long actorId, T payload) {
    public EventEnvelope {
        if (eventId == null || occurredAt == null || correlationId == null || payload == null) {
            throw new IllegalArgumentException("Event metadata and payload are required");
        }
        if (eventType == null || !eventType.matches("[a-z]+\\.[a-z]+(?:-[a-z]+)*\\.v[1-9][0-9]*")) {
            throw new IllegalArgumentException("Invalid eventType routing key");
        }
        if (schemaVersion < 1 || aggregateVersion < 1 || aggregateType == null || aggregateType.isBlank()
                || aggregateId == null || aggregateId.isBlank() || actorId != null && actorId <= 0) {
            throw new IllegalArgumentException("Invalid event aggregate metadata");
        }
    }

    public static <T> EventEnvelope<T> create(String eventType, String aggregateType, String aggregateId,
                                               long aggregateVersion, UUID correlationId, UUID causationId,
                                               Long actorId, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, 1, aggregateType, aggregateId,
                aggregateVersion, Instant.now(), correlationId, causationId, actorId, payload);
    }
}
