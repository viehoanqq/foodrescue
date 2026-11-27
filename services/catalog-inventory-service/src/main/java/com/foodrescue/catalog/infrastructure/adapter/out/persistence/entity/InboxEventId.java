package com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class InboxEventId implements Serializable {
    private String consumerName;
    private UUID eventId;

    public InboxEventId() {}

    public InboxEventId(String consumerName, UUID eventId) {
        this.consumerName = consumerName;
        this.eventId = eventId;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof InboxEventId that)) return false;
        return Objects.equals(consumerName, that.consumerName) && Objects.equals(eventId, that.eventId);
    }

    @Override public int hashCode() { return Objects.hash(consumerName, eventId); }
}
