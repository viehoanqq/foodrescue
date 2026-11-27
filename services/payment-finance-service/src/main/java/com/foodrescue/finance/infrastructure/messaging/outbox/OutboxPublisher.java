package com.foodrescue.finance.infrastructure.messaging.outbox;

import com.foodrescue.finance.infrastructure.adapter.out.persistence.JpaFinanceQueries;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private final JpaFinanceQueries db;
    private final RabbitTemplate rabbit;
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(OutboxPublisher.class);

    public OutboxPublisher(JpaFinanceQueries db, RabbitTemplate rabbit) {
        this.db = db;
        this.rabbit = rabbit;
        rabbit.setMandatory(true);
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:1000}")
    public void publish() {
        // Atomic claim commits before waiting on RabbitMQ. One row keeps the lease longer than the
        // confirm timeout.
        for (int i = 0; i < 50; i++) {
            UUID token = UUID.randomUUID();
            var rows =
                    db.queryForList(
                            """
UPDATE outbox_events SET claim_token=?,claimed_until=now()+interval '30 seconds'
WHERE event_id IN (SELECT event_id FROM outbox_events WHERE published_at IS NULL
AND (next_attempt_at IS NULL OR next_attempt_at<=now()) AND (claimed_until IS NULL OR claimed_until<now())
ORDER BY occurred_at LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING event_id,event_type,payload
""",
                            token);
            if (rows.isEmpty()) return;
            var row = rows.get(0);
            UUID id = (UUID) row.get("event_id");
            try {
                var properties = new MessageProperties();
                properties.setContentType("application/json");
                properties.setMessageId(id.toString());
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                var correlation = new CorrelationData(id.toString());
                rabbit.send(
                        "foodrescue.events",
                        (String) row.get("event_type"),
                        new Message(
                                row.get("payload").toString().getBytes(StandardCharsets.UTF_8),
                                properties),
                        correlation);
                var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck() || correlation.getReturned() != null)
                    throw new IllegalStateException("Publish not routed/confirmed");
                db.update(
                        "UPDATE outbox_events SET"
                            + " published_at=now(),attempts=attempts+1,claim_token=NULL,claimed_until=NULL,last_error=NULL"
                            + " WHERE event_id=? AND claim_token=?",
                        id,
                        token);
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                db.update(
                        """
UPDATE outbox_events SET attempts=attempts+1,next_attempt_at=now()+make_interval(secs=>LEAST(300,POWER(2,LEAST(attempts,8)))::double precision),
last_error=?,claim_token=NULL,claimed_until=NULL WHERE event_id=? AND claim_token=?
""",
                        ex.getClass().getSimpleName(),
                        id,
                        token);
                LOG.warn("Finance outbox publish deferred: {}", id);
                return;
            }
        }
    }
}
