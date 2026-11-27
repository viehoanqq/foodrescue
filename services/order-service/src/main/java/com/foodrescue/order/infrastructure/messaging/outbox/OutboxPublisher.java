package com.foodrescue.order.infrastructure.messaging.outbox;

import com.foodrescue.buildingblocks.events.MessagingNames;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
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
        for (JpaOutboxRepository.PendingEvent row : outbox.findPendingForPublish()) {
            String id = row.id();
            String routingKey = row.type();
            String payload = row.payload();
            CorrelationData correlation = new CorrelationData(id);
            rabbit.invoke(ops -> {
                ops.convertAndSend(MessagingNames.EVENTS_EXCHANGE, routingKey, payload,
                    message -> {
                        message.getMessageProperties().setMessageId(id);
                        message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    }, correlation);
                ops.waitForConfirmsOrDie(5000);
                return null;
            });
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ returned unroutable event " + id);
            }
            outbox.markPublished(id);
        }
    }
}
