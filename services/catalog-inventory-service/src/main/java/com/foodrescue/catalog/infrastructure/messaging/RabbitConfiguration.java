package com.foodrescue.catalog.infrastructure.messaging;

import com.foodrescue.buildingblocks.events.MessagingNames;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    @Bean
    public TopicExchange foodrescueEvents() {
        return new TopicExchange(MessagingNames.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange foodrescueDlx() {
        return new DirectExchange(MessagingNames.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue catalogStoreStatus() {
        return QueueBuilder.durable(CatalogMessaging.STORE_STATUS_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", MessagingNames.DEAD_LETTER_EXCHANGE,
                        "x-dead-letter-routing-key", CatalogMessaging.STORE_STATUS_DLQ_ROUTING_KEY
                ))
                .build();
    }

    @Bean
    public Binding catalogStoreStatusBinding(Queue catalogStoreStatus, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(catalogStoreStatus).to(foodrescueEvents)
                .with(MessagingNames.STORE_STATUS_CHANGED_EVENT);
    }

    @Bean
    public Queue catalogStoreStatusDeadLetters() {
        return new Queue(CatalogMessaging.STORE_STATUS_DEAD_QUEUE, true);
    }

    @Bean
    public Binding catalogStoreStatusDeadBinding(Queue catalogStoreStatusDeadLetters, DirectExchange foodrescueDlx) {
        return BindingBuilder.bind(catalogStoreStatusDeadLetters).to(foodrescueDlx)
                .with(CatalogMessaging.STORE_STATUS_DLQ_ROUTING_KEY);
    }
}
