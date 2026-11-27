package com.foodrescue.order.infrastructure.messaging.inbox;

import com.foodrescue.order.application.port.out.IInboxPort;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Repository
public class JpaInboxAdapter implements IInboxPort {
    private final EntityManager em;
    public JpaInboxAdapter(EntityManager em) { this.em = em; }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public boolean claim(String consumerName, UUID eventId) {
        return em.createNativeQuery("INSERT IGNORE INTO inbox_events(consumer_name,event_id,processed_at) " +
                "VALUES (:consumer,:event,CURRENT_TIMESTAMP(6))")
                .setParameter("consumer", consumerName).setParameter("event", eventId.toString())
                .executeUpdate() == 1;
    }

    @Override @Transactional(readOnly = true)
    public boolean isProcessed(String consumerName, UUID eventId) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM inbox_events " +
                "WHERE consumer_name=:consumer AND event_id=:event")
                .setParameter("consumer", consumerName).setParameter("event", eventId.toString())
                .getSingleResult()).longValue() > 0;
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void record(String consumerName, UUID eventId) { claim(consumerName, eventId); }
}
