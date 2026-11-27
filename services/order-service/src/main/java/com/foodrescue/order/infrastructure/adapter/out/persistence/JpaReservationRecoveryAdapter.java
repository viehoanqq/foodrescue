package com.foodrescue.order.infrastructure.adapter.out.persistence;

import com.foodrescue.order.application.port.out.IReservationRecoveryPort;
import com.foodrescue.order.domain.exception.OrderException;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaReservationRecoveryAdapter implements IReservationRecoveryPort {
    private final EntityManager em;
    public JpaReservationRecoveryAdapter(EntityManager em) { this.em = em; }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensurePending(UUID id) {
        em.createNativeQuery("INSERT IGNORE INTO reservation_recovery(reservation_id,next_attempt_at) " +
                "VALUES (:id,DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 2 MINUTE))")
                .setParameter("id", id.toString()).executeUpdate();
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void lockPending(UUID id) {
        String status = (String) em.createNativeQuery("SELECT status FROM reservation_recovery " +
                "WHERE reservation_id=:id FOR UPDATE").setParameter("id", id.toString()).getSingleResult();
        if (!"PENDING".equals(status))
            throw OrderException.conflict("RESERVATION_ALREADY_USED",
                    "This checkout reservation was already completed or released");
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void markOrdered(UUID id, Long orderId) {
        int updated = em.createNativeQuery("UPDATE reservation_recovery SET status='ORDERED',order_id=:orderId," +
                "updated_at=CURRENT_TIMESTAMP(6) WHERE reservation_id=:id AND status='PENDING'")
                .setParameter("orderId", orderId).setParameter("id", id.toString()).executeUpdate();
        if (updated != 1) throw new IllegalStateException("Reservation recovery state changed during checkout");
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestRelease(UUID id) {
        em.createNativeQuery("UPDATE reservation_recovery SET next_attempt_at=CURRENT_TIMESTAMP(6)," +
                "updated_at=CURRENT_TIMESTAMP(6) WHERE reservation_id=:id AND status='PENDING'")
                .setParameter("id", id.toString()).executeUpdate();
    }

    @Override @Transactional(readOnly = true)
    public List<UUID> findDue(int limit) {
        return em.createNativeQuery("SELECT reservation_id FROM reservation_recovery " +
                "WHERE status IN ('PENDING','RECOVERING') AND next_attempt_at<=CURRENT_TIMESTAMP(6) " +
                "ORDER BY next_attempt_at")
                .setMaxResults(Math.max(1, Math.min(limit, 100))).getResultStream()
                .map(value -> UUID.fromString(value.toString())).toList();
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaim(UUID id) {
        return em.createNativeQuery("UPDATE reservation_recovery SET status='RECOVERING',attempts=attempts+1," +
                "next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 90 SECOND),updated_at=CURRENT_TIMESTAMP(6) " +
                "WHERE reservation_id=:id AND status IN ('PENDING','RECOVERING') " +
                "AND next_attempt_at<=CURRENT_TIMESTAMP(6)")
                .setParameter("id", id.toString()).executeUpdate() == 1;
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReleased(UUID id) {
        em.createNativeQuery("UPDATE reservation_recovery SET status='RELEASED',last_error=NULL," +
                "updated_at=CURRENT_TIMESTAMP(6) WHERE reservation_id=:id AND status='RECOVERING'")
                .setParameter("id", id.toString()).executeUpdate();
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOrderFound(UUID id, Long orderId) {
        em.createNativeQuery("UPDATE reservation_recovery SET status='ORDERED',order_id=:orderId,last_error=NULL," +
                "updated_at=CURRENT_TIMESTAMP(6) WHERE reservation_id=:id AND status='RECOVERING'")
                .setParameter("orderId", orderId).setParameter("id", id.toString()).executeUpdate();
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markAbsentIfOld(UUID id) {
        return em.createNativeQuery("UPDATE reservation_recovery SET status='RELEASED',last_error=NULL," +
                "updated_at=CURRENT_TIMESTAMP(6) WHERE reservation_id=:id AND status='RECOVERING' " +
                "AND created_at<DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 30 MINUTE)")
                .setParameter("id", id.toString()).executeUpdate() == 1;
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryLater(UUID id, String error) {
        String safe = error == null ? "Unknown Catalog error" : error.substring(0, Math.min(error.length(), 500));
        em.createNativeQuery("UPDATE reservation_recovery SET status='PENDING',last_error=:error," +
                "next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL LEAST(300,POW(2,LEAST(attempts,8))) SECOND)," +
                "updated_at=CURRENT_TIMESTAMP(6) WHERE reservation_id=:id AND status='RECOVERING'")
                .setParameter("error", safe).setParameter("id", id.toString()).executeUpdate();
    }
}
