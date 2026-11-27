package com.foodrescue.identity.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inbox_events")
public class InboxEventEntity {
    @EmbeddedId
    private Key id;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected InboxEventEntity() {}

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "consumer_name", length = 100, nullable = false)
        private String consumerName;

        @JdbcTypeCode(SqlTypes.CHAR)
        @Column(name = "event_id", columnDefinition = "char(36)", length = 36, nullable = false)
        private String eventId;

        public Key() {}
        public Key(String consumerName, String eventId) {
            this.consumerName = consumerName;
            this.eventId = eventId;
        }

        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(consumerName, key.consumerName)
                    && Objects.equals(eventId, key.eventId);
        }

        @Override public int hashCode() { return Objects.hash(consumerName, eventId); }
    }
}
