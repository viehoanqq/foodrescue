package com.foodrescue.identity.infrastructure.messaging;

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

    @Bean
    public Queue identityAuditEvents() {
        return org.springframework.amqp.core.QueueBuilder.durable(MessagingNames.IDENTITY_AUDIT_QUEUE)
                .deadLetterExchange(MessagingNames.EVENTS_EXCHANGE)
                .deadLetterRoutingKey(MessagingNames.AUDIT_ACTION_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding identityAuditBinding(Queue identityAuditEvents, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(identityAuditEvents).to(foodrescueEvents).with(MessagingNames.AUDIT_ACTION_EVENT);
    }

    @Bean
    public Queue identityAuditDeadLetters() { return new Queue(MessagingNames.IDENTITY_AUDIT_DEAD_QUEUE, true); }

    @Bean
    public Binding identityAuditDeadBinding(Queue identityAuditDeadLetters, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(identityAuditDeadLetters).to(foodrescueEvents).with(MessagingNames.AUDIT_ACTION_DEAD_ROUTING_KEY);
    }

    @Bean
    public Queue identityStoreStatus() { return new Queue(MessagingNames.IDENTITY_STORE_STATUS_QUEUE, true); }

    @Bean
    public Binding identityStoreStatusBinding(Queue identityStoreStatus, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(identityStoreStatus).to(foodrescueEvents)
                .with(MessagingNames.STORE_STATUS_CHANGED_EVENT);
    }
}
