package com.foodrescue.store.infrastructure.messaging.outbox;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaOutboxRepository {
    private final EntityManager em;
    public JpaOutboxRepository(EntityManager em) { this.em = em; }

    @Transactional
    public List<PendingEvent> claimBatch(int size, int leaseSeconds) {
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery(
                "SELECT event_id,event_type,payload,attempts FROM outbox_events " +
                "WHERE published_at IS NULL AND (next_attempt_at IS NULL OR next_attempt_at<=CURRENT_TIMESTAMP(6)) " +
                "AND (claim_until IS NULL OR claim_until<=CURRENT_TIMESTAMP(6)) " +
                "ORDER BY occurred_at,event_id LIMIT " + size + " FOR UPDATE SKIP LOCKED").getResultList();
        return rows.stream().map(r -> {
            String payload = r[2] instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : r[2].toString();
            var event = new PendingEvent((String) r[0], (String) r[1], payload,
                    ((Number) r[3]).intValue() + 1, UUID.randomUUID().toString());
            em.createNativeQuery("UPDATE outbox_events SET claim_token=:token," +
                    "claim_until=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL :lease SECOND), attempts=attempts+1 " +
                    "WHERE event_id=:id")
                    .setParameter("token", event.claimToken()).setParameter("lease", leaseSeconds)
                    .setParameter("id", event.eventId()).executeUpdate();
            return event;
        }).toList();
    }

    @Transactional
    public void markPublished(PendingEvent event) {
        em.createNativeQuery("UPDATE outbox_events SET published_at=CURRENT_TIMESTAMP(6), " +
                "claim_token=NULL,claim_until=NULL,next_attempt_at=NULL,last_error=NULL " +
                "WHERE event_id=:id AND claim_token=:token AND published_at IS NULL")
                .setParameter("id", event.eventId()).setParameter("token", event.claimToken()).executeUpdate();
    }

    @Transactional
    public void markFailed(PendingEvent event, int delay, String error) {
        em.createNativeQuery("UPDATE outbox_events SET claim_token=NULL,claim_until=NULL," +
                "next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL :delay SECOND),last_error=:error " +
                "WHERE event_id=:id AND claim_token=:token AND published_at IS NULL")
                .setParameter("delay", delay).setParameter("error", error)
                .setParameter("id", event.eventId()).setParameter("token", event.claimToken()).executeUpdate();
    }

    public record PendingEvent(String eventId, String eventType, String payload,
                               int attempts, String claimToken) {}
}
