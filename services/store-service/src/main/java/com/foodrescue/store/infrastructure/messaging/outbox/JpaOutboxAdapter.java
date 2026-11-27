package com.foodrescue.store.infrastructure.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.store.application.port.out.IOutboxPort;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaOutboxAdapter implements IOutboxPort {
    private final EntityManager em;
    private final ObjectMapper json;

    public JpaOutboxAdapter(EntityManager em, ObjectMapper json) { this.em = em; this.json = json; }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(EventEnvelope<?> event) {
        try {
            em.createNativeQuery("INSERT INTO outbox_events " +
                    "(event_id,aggregate_type,aggregate_id,aggregate_version,event_type,schema_version,payload,occurred_at) " +
                    "VALUES (:id,:aggregateType,:aggregateId,:version,:type,:schemaVersion,:payload,:occurredAt)")
                    .setParameter("id", event.eventId().toString())
                    .setParameter("aggregateType", event.aggregateType())
                    .setParameter("aggregateId", event.aggregateId())
                    .setParameter("version", event.aggregateVersion())
                    .setParameter("type", event.eventType())
                    .setParameter("schemaVersion", event.schemaVersion())
                    .setParameter("payload", json.writeValueAsString(event))
                    .setParameter("occurredAt", LocalDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                    .executeUpdate();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize Store outbox event", ex);
        }
    }
}
