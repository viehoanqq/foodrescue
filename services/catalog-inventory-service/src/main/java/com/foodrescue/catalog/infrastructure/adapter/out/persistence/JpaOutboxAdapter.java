package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.catalog.application.port.out.IOutboxPort;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.OutboxEventEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaOutboxAdapter implements IOutboxPort {
    @PersistenceContext
    private EntityManager entityManager;
    private final ObjectMapper json;

    public JpaOutboxAdapter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(EventEnvelope<?> event) {
        try {
            JsonNode payload = json.readTree(json.writeValueAsString(event));
            entityManager.persist(new OutboxEventEntity(event.eventId(), event.aggregateType(),
                    event.aggregateId(), event.aggregateVersion(), event.eventType(), event.schemaVersion(),
                    payload, event.occurredAt()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize Catalog outbox event", ex);
        }
    }
}
