package com.foodrescue.identity.infrastructure.messaging;

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
        return new TopicExchange("foodrescue.events", true, false);
    }

    // Temporary durable catch-all until business consumers are migrated.
    @Bean
    public Queue migrationEvents() { return new Queue("foodrescue.migration-events", true); }

    @Bean
    public Binding migrationBinding(Queue migrationEvents, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(migrationEvents).to(foodrescueEvents).with("#");
    }

    @Bean
    public Queue identityAuditEvents() {
        return org.springframework.amqp.core.QueueBuilder.durable("identity.audit-events")
                .deadLetterExchange("foodrescue.events")
                .deadLetterRoutingKey("audit.action.dead")
                .build();
    }

    @Bean
    public Binding identityAuditBinding(Queue identityAuditEvents, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(identityAuditEvents).to(foodrescueEvents).with("audit.action.v1");
    }

    @Bean
    public Queue identityAuditDeadLetters() { return new Queue("identity.audit-dead", true); }

    @Bean
    public Binding identityAuditDeadBinding(Queue identityAuditDeadLetters, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(identityAuditDeadLetters).to(foodrescueEvents).with("audit.action.dead");
    }
}
