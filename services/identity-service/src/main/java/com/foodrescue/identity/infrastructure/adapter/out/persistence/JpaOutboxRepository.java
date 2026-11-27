package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOutboxRepository {
    private final EntityManager entityManager;

    public JpaOutboxRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<PendingEvent> findPendingForPublish() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT event_id, event_type, CAST(payload AS CHAR) FROM outbox_events "
                        + "WHERE published_at IS NULL "
                        + "AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP(6)) "
                        + "ORDER BY occurred_at LIMIT 50 FOR UPDATE SKIP LOCKED")
                .getResultList();
        return rows.stream().map(row -> new PendingEvent(
                (String) row[0], (String) row[1], (String) row[2])).toList();
    }

    public void markPublished(String eventId) {
        entityManager.createNativeQuery("UPDATE outbox_events SET published_at=CURRENT_TIMESTAMP(6), "
                        + "attempts=attempts+1 WHERE event_id=:eventId")
                .setParameter("eventId", eventId).executeUpdate();
    }

    public record PendingEvent(String id, String type, String payload) {}
}
