package com.foodrescue.order.infrastructure.messaging.outbox;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** At least once publisher. Consumers must deduplicate eventId in inbox_events. */
@Component
public class OutboxPublisher {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;

    public OutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:1000}")
    @Transactional
    public void publish() {
        List<Map<String, Object>> rows = jdbc.query(
            "SELECT event_id, event_type, payload FROM outbox_events " +
            "WHERE published_at IS NULL AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP) " +
            "ORDER BY occurred_at LIMIT 50 FOR UPDATE SKIP LOCKED",
            (ResultSet rs, int row) -> Map.of(
                "id", rs.getString("event_id"),
                "type", rs.getString("event_type"),
                "payload", rs.getString("payload")));
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("id");
            String routingKey = (String) row.get("type");
            String payload = (String) row.get("payload");
            rabbit.invoke(ops -> {
                ops.convertAndSend("foodrescue.events", routingKey, payload,
                    message -> { message.getMessageProperties().setMessageId(id); return message; });
                ops.waitForConfirmsOrDie(5000);
                return null;
            });
            jdbc.update("UPDATE outbox_events SET published_at = CURRENT_TIMESTAMP, attempts = attempts + 1 WHERE event_id = CAST(? AS UUID)", id);
        }
    }
}
