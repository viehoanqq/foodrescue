package com.foodrescue.finance.infrastructure.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class RabbitConfiguration {
    @Bean
    public Declarables financeTopology() {
        TopicExchange events = new TopicExchange("foodrescue.events", true, false);
        DirectExchange dlx = new DirectExchange("foodrescue.dlx", true, false);
        java.util.List<Declarable> declarations = new java.util.ArrayList<>();
        declarations.add(events);
        declarations.add(dlx);
        String[][] routes = {
            {"finance.order-snapshots", "order.state-changed.v1"},
            {"finance.refund-required", "order.refund-required.v1"}
        };
        for (String[] route : routes) {
            Queue queue =
                    QueueBuilder.durable(route[0])
                            .deadLetterExchange("foodrescue.dlx")
                            .deadLetterRoutingKey(route[0])
                            .build();
            Queue dead = QueueBuilder.durable(route[0] + ".dead").build();
            declarations.add(queue);
            declarations.add(dead);
            declarations.add(BindingBuilder.bind(queue).to(events).with(route[1]));
            declarations.add(BindingBuilder.bind(dead).to(dlx).with(route[0]));
        }
        return new Declarables(declarations);
    }
}
