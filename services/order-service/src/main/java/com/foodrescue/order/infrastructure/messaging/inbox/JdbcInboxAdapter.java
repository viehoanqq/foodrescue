package com.foodrescue.order.infrastructure.messaging.inbox;

import com.foodrescue.order.application.port.out.IInboxPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInboxAdapter implements IInboxPort {
    private final JdbcTemplate jdbc;

    public JdbcInboxAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isProcessed(String consumerName, UUID eventId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inbox_events WHERE consumer_name = ? AND event_id = ?",
                Integer.class, consumerName, eventId.toString());
        return count != null && count > 0;
    }

    @Override
    public void record(String consumerName, UUID eventId) {
        try {
            jdbc.update("INSERT INTO inbox_events (consumer_name, event_id, processed_at) VALUES (?, ?, ?)",
                    consumerName, eventId.toString(), Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException e) {
            // Already recorded idempotently
        }
    }
}
