package com.foodrescue.catalog.infrastructure.messaging;

import com.foodrescue.buildingblocks.events.MessagingNames;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    @Bean
    public TopicExchange foodrescueEvents() {
        return new TopicExchange(MessagingNames.EVENTS_EXCHANGE, true, false);
    }

    // Temporary durable catch-all until business consumers are migrated.
    @Bean
    public Queue migrationEvents() { return new Queue(MessagingNames.MIGRATION_QUEUE, true); }

    @Bean
    public Binding migrationBinding(Queue migrationEvents, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(migrationEvents).to(foodrescueEvents).with(MessagingNames.ALL_EVENTS_ROUTING_KEY);
    }
}
