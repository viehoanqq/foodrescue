package com.foodrescue.store.infrastructure.messaging.outbox;

import com.foodrescue.buildingblocks.events.MessagingNames;
import java.util.List;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Publishes committed Store outbox events at least once. Consumers deduplicate eventId. */
@Component
public class OutboxPublisher {
    private static final int BATCH_SIZE = 20;
    private static final int LEASE_SECONDS = 180;
    private final JpaOutboxRepository outbox;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transactions;

    public OutboxPublisher(JpaOutboxRepository outbox, RabbitTemplate rabbit, TransactionTemplate transactions) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:1000}")
    public void publish() {
        List<JpaOutboxRepository.PendingEvent> batch = transactions.execute(status -> outbox.claimBatch(BATCH_SIZE, LEASE_SECONDS));
        if (batch == null) return;
        for (JpaOutboxRepository.PendingEvent event : batch) {
            try {
                CorrelationData correlation = new CorrelationData(event.eventId());
                rabbit.invoke(ops -> {
                    ops.convertAndSend(MessagingNames.EVENTS_EXCHANGE, event.eventType(), event.payload(), message -> {
                        message.getMessageProperties().setMessageId(event.eventId());
                        message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    }, correlation);
                    ops.waitForConfirmsOrDie(5000);
                    return null;
                });
                if (correlation.getReturned() != null)
                    throw new IllegalStateException("RabbitMQ returned unroutable event " + event.eventId());
                outbox.markPublished(event);
            } catch (Exception ex) {
                String error = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
                outbox.markFailed(event, retryDelay(event.attempts()),
                        error.substring(0, Math.min(error.length(), 1000)));
            }
        }
    }

    private int retryDelay(int attempts) {
        return Math.min(300, 1 << Math.min(attempts, 8));
    }

}
