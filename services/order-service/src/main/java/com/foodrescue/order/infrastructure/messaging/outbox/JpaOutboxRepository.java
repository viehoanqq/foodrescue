package com.foodrescue.order.infrastructure.messaging.outbox;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaOutboxRepository {
    private final EntityManager em;
    public JpaOutboxRepository(EntityManager em) { this.em = em; }

    @Transactional
    public List<PendingEvent> findPendingForPublish() {
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery(
                "SELECT event_id,event_type,payload FROM outbox_events " +
                "WHERE published_at IS NULL AND (next_attempt_at IS NULL OR next_attempt_at<=CURRENT_TIMESTAMP) " +
                "ORDER BY occurred_at LIMIT 50 FOR UPDATE SKIP LOCKED").getResultList();
        return rows.stream().map(row -> new PendingEvent((String) row[0], (String) row[1],
                row[2] instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : row[2].toString())).toList();
    }

    @Transactional
    public void markPublished(String id) {
        em.createNativeQuery("UPDATE outbox_events SET published_at=CURRENT_TIMESTAMP," +
                "attempts=attempts+1 WHERE event_id=:id")
                .setParameter("id", id).executeUpdate();
    }

    public record PendingEvent(String id, String type, String payload) {}
}
