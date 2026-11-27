package com.foodrescue.order.infrastructure.adapter.out.persistence;

import com.foodrescue.order.application.port.out.IReservationRecoveryPort;
import com.foodrescue.order.domain.exception.OrderException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcReservationRecoveryAdapter implements IReservationRecoveryPort {
    private final JdbcTemplate jdbc;

    public JdbcReservationRecoveryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensurePending(UUID id) {
        jdbc.update("INSERT IGNORE INTO reservation_recovery(reservation_id, next_attempt_at) " +
                "VALUES (?, DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 2 MINUTE))", id.toString());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockPending(UUID id) {
        String status = jdbc.queryForObject(
                "SELECT status FROM reservation_recovery WHERE reservation_id=? FOR UPDATE", String.class, id.toString());
        if (!"PENDING".equals(status)) {
            throw OrderException.conflict("RESERVATION_ALREADY_USED", "This checkout reservation was already completed or released");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markOrdered(UUID id, Long orderId) {
        int updated = jdbc.update("UPDATE reservation_recovery SET status='ORDERED', order_id=?, updated_at=CURRENT_TIMESTAMP(6) " +
                "WHERE reservation_id=? AND status='PENDING'", orderId, id.toString());
        if (updated != 1) throw new IllegalStateException("Reservation recovery state changed during checkout");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestRelease(UUID id) {
        jdbc.update("UPDATE reservation_recovery SET next_attempt_at=CURRENT_TIMESTAMP(6), updated_at=CURRENT_TIMESTAMP(6) " +
                "WHERE reservation_id=? AND status='PENDING'", id.toString());
    }

    @Override
    public List<UUID> findDue(int limit) {
        return jdbc.query("SELECT reservation_id FROM reservation_recovery " +
                        "WHERE status IN ('PENDING','RECOVERING') AND next_attempt_at<=CURRENT_TIMESTAMP(6) " +
                        "ORDER BY next_attempt_at LIMIT " + Math.max(1, Math.min(limit, 100)),
                (rs, row) -> UUID.fromString(rs.getString(1)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaim(UUID id) {
        return jdbc.update("UPDATE reservation_recovery SET status='RECOVERING', attempts=attempts+1, " +
                        "next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 90 SECOND), " +
                        "updated_at=CURRENT_TIMESTAMP(6) WHERE reservation_id=? AND status IN ('PENDING','RECOVERING') " +
                        "AND next_attempt_at<=CURRENT_TIMESTAMP(6)", id.toString()) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReleased(UUID id) {
        jdbc.update("UPDATE reservation_recovery SET status='RELEASED', last_error=NULL, " +
                "updated_at=CURRENT_TIMESTAMP(6) WHERE reservation_id=? AND status='RECOVERING'", id.toString());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOrderFound(UUID id, Long orderId) {
        jdbc.update("UPDATE reservation_recovery SET status='ORDERED', order_id=?, last_error=NULL, " +
                "updated_at=CURRENT_TIMESTAMP(6) WHERE reservation_id=? AND status='RECOVERING'", orderId, id.toString());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markAbsentIfOld(UUID id) {
        return jdbc.update("UPDATE reservation_recovery SET status='RELEASED', last_error=NULL, " +
                "updated_at=CURRENT_TIMESTAMP(6) WHERE reservation_id=? AND status='RECOVERING' " +
                "AND created_at<DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 30 MINUTE)", id.toString()) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryLater(UUID id, String error) {
        String safe = error == null ? "Unknown Catalog error" : error.substring(0, Math.min(error.length(), 500));
        jdbc.update("UPDATE reservation_recovery SET status='PENDING', last_error=?, " +
                        "next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL LEAST(300, POW(2, LEAST(attempts, 8))) SECOND), " +
                        "updated_at=CURRENT_TIMESTAMP(6) WHERE reservation_id=? AND status='RECOVERING'", safe, id.toString());
    }
}
