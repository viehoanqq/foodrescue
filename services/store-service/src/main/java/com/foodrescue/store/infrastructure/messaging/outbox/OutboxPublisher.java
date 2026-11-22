package com.foodrescue.store.infrastructure.messaging.outbox;

import com.foodrescue.buildingblocks.events.MessagingNames;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Publishes committed Store outbox events at least once. Consumers deduplicate eventId. */
@Component
public class OutboxPublisher {
    private static final int BATCH_SIZE = 20;
    private static final int LEASE_SECONDS = 180;
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transactions;

    public OutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:1000}")
    public void publish() {
        List<PendingEvent> batch = transactions.execute(status -> claimBatch());
        if (batch == null) return;
        for (PendingEvent event : batch) {
            try {
                rabbit.invoke(ops -> {
                    ops.convertAndSend(MessagingNames.EVENTS_EXCHANGE, event.eventType(), event.payload(), message -> {
                        message.getMessageProperties().setMessageId(event.eventId());
                        message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    });
                    ops.waitForConfirmsOrDie(5000);
                    return null;
                });
                jdbc.update("UPDATE outbox_events SET published_at=CURRENT_TIMESTAMP(6), " +
                                "claim_token=NULL, claim_until=NULL, next_attempt_at=NULL, last_error=NULL " +
                                "WHERE event_id=? AND claim_token=? AND published_at IS NULL",
                        event.eventId(), event.claimToken());
            } catch (Exception ex) {
                String error = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
                jdbc.update("UPDATE outbox_events SET claim_token=NULL, claim_until=NULL, " +
                                "next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL ? SECOND), last_error=? " +
                                "WHERE event_id=? AND claim_token=? AND published_at IS NULL",
                        retryDelay(event.attempts()), error.substring(0, Math.min(error.length(), 1000)),
                        event.eventId(), event.claimToken());
            }
        }
    }

    private List<PendingEvent> claimBatch() {
        List<PendingEvent> rows = jdbc.query(
                "SELECT event_id,event_type,payload,attempts FROM outbox_events " +
                "WHERE published_at IS NULL AND (next_attempt_at IS NULL OR next_attempt_at<=CURRENT_TIMESTAMP(6)) " +
                "AND (claim_until IS NULL OR claim_until<=CURRENT_TIMESTAMP(6)) " +
                "ORDER BY occurred_at,event_id LIMIT " + BATCH_SIZE + " FOR UPDATE SKIP LOCKED",
                (ResultSet rs, int row) -> new PendingEvent(rs.getString("event_id"),
                        rs.getString("event_type"), rs.getString("payload"),
                        rs.getInt("attempts") + 1, UUID.randomUUID().toString()));
        for (PendingEvent event : rows) {
            jdbc.update("UPDATE outbox_events SET claim_token=?, " +
                            "claim_until=DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL ? SECOND), attempts=attempts+1 " +
                            "WHERE event_id=?", event.claimToken(), LEASE_SECONDS, event.eventId());
        }
        return rows;
    }

    private int retryDelay(int attempts) {
        return Math.min(300, 1 << Math.min(attempts, 8));
    }

    private record PendingEvent(String eventId, String eventType, String payload,
                                int attempts, String claimToken) {}
}
