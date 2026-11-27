package com.foodrescue.catalog.infrastructure.messaging.outbox;

import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.OutboxEventEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** At least once publisher. Consumers must deduplicate eventId in inbox_events. */
@Component
public class OutboxPublisher {
    private final RabbitTemplate rabbit;

    @PersistenceContext
    private EntityManager entityManager;

    public OutboxPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:1000}")
    @Transactional
    public void publish() {
        @SuppressWarnings("unchecked")
        List<OutboxEventEntity> events = entityManager.createNativeQuery(
                "SELECT * FROM outbox_events " +
                "WHERE published_at IS NULL AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP) " +
                "ORDER BY occurred_at LIMIT 50 FOR UPDATE SKIP LOCKED", OutboxEventEntity.class)
                .getResultList();
        for (OutboxEventEntity event : events) {
            rabbit.invoke(ops -> {
                ops.convertAndSend(MessagingNames.EVENTS_EXCHANGE, event.getEventType(), event.getPayload().toString(),
                    message -> {
                        message.getMessageProperties().setMessageId(event.getEventId().toString());
                        message.getMessageProperties().setContentType("application/json");
                        message.getMessageProperties().setDeliveryMode(
                                org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                        return message;
                    });
                ops.waitForConfirmsOrDie(5000);
                return null;
            });
            event.markPublished(Instant.now());
        }
    }
}
