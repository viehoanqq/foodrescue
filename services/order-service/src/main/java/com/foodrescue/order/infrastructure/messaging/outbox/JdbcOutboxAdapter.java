package com.foodrescue.order.infrastructure.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.order.application.port.out.IOutboxPort;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcOutboxAdapter implements IOutboxPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcOutboxAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(EventEnvelope<?> event) {
        try {
            jdbc.update("INSERT INTO outbox_events " +
                    "(event_id, aggregate_type, aggregate_id, aggregate_version, event_type, schema_version, payload, occurred_at) " +
                    "VALUES (CAST(? AS UUID), ?, ?, ?, ?, ?, CAST(? AS JSONB), ?)",
                    event.eventId().toString(),
                    event.aggregateType(),
                    event.aggregateId(),
                    event.aggregateVersion(),
                    event.eventType(),
                    event.schemaVersion(),
                    json.writeValueAsString(event),
                    Timestamp.from(event.occurredAt()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize Order outbox event", ex);
        }
    }
}
