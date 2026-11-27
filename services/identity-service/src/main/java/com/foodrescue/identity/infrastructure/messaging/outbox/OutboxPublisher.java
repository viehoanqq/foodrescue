package com.foodrescue.identity.infrastructure.messaging.outbox;

import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.JpaOutboxRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** At least once publisher. Consumers must deduplicate eventId in inbox_events. */
@Component
public class OutboxPublisher {
    private final JpaOutboxRepository outbox;
    private final RabbitTemplate rabbit;

    public OutboxPublisher(JpaOutboxRepository outbox, RabbitTemplate rabbit) {
        this.outbox = outbox;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-ms:1000}")
    @Transactional
    public void publish() {
        for (JpaOutboxRepository.PendingEvent event : outbox.findPendingForPublish()) {
            String id = event.id();
            rabbit.invoke(ops -> {
                ops.convertAndSend(MessagingNames.EVENTS_EXCHANGE, event.type(), event.payload(),
                    message -> { message.getMessageProperties().setMessageId(id); return message; });
                ops.waitForConfirmsOrDie(5000);
                return null;
            });
            outbox.markPublished(id);
        }
    }
}
