package com.foodrescue.order.infrastructure.messaging;

import com.foodrescue.buildingblocks.events.MessagingNames;
import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitConfiguration {
    @Bean
    public RabbitTemplateCustomizer orderPublisherReturns() {
        return template -> template.setMandatory(true);
    }

    @Bean
    public TopicExchange foodrescueEvents() {
        return new TopicExchange(MessagingNames.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange foodrescueDlx() {
        return new DirectExchange(MessagingNames.DEAD_LETTER_EXCHANGE, true, false);
    }

    // 1. Payment Results Queue & Bindings
    @Bean
    public Queue orderPaymentResultsQueue() {
        return QueueBuilder.durable(MessagingNames.ORDER_PAYMENT_RESULTS_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", MessagingNames.DEAD_LETTER_EXCHANGE,
                        "x-dead-letter-routing-key", MessagingNames.ORDER_PAYMENT_RESULTS_QUEUE
                ))
                .build();
    }

    @Bean
    public Queue orderPaymentResultsDeadQueue() {
        return QueueBuilder.durable(MessagingNames.ORDER_PAYMENT_RESULTS_DEAD_QUEUE).build();
    }

    @Bean
    public Binding dlqPaymentBinding(Queue orderPaymentResultsDeadQueue, DirectExchange foodrescueDlx) {
        return BindingBuilder.bind(orderPaymentResultsDeadQueue).to(foodrescueDlx).with(MessagingNames.ORDER_PAYMENT_RESULTS_QUEUE);
    }

    @Bean
    public Binding paymentSucceededBinding(Queue orderPaymentResultsQueue, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(orderPaymentResultsQueue).to(foodrescueEvents).with(MessagingNames.PAYMENT_SUCCEEDED_EVENT);
    }

    @Bean
    public Binding paymentFailedBinding(Queue orderPaymentResultsQueue, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(orderPaymentResultsQueue).to(foodrescueEvents).with(MessagingNames.PAYMENT_FAILED_EVENT);
    }

    // 2. Refund Status Queue & Bindings
    @Bean
    public Queue orderRefundStatusQueue() {
        return QueueBuilder.durable(MessagingNames.ORDER_REFUND_STATUS_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", MessagingNames.DEAD_LETTER_EXCHANGE,
                        "x-dead-letter-routing-key", MessagingNames.ORDER_REFUND_STATUS_QUEUE
                ))
                .build();
    }

    @Bean
    public Queue orderRefundStatusDeadQueue() {
        return QueueBuilder.durable(MessagingNames.ORDER_REFUND_STATUS_DEAD_QUEUE).build();
    }

    @Bean
    public Binding dlqRefundBinding(Queue orderRefundStatusDeadQueue, DirectExchange foodrescueDlx) {
        return BindingBuilder.bind(orderRefundStatusDeadQueue).to(foodrescueDlx).with(MessagingNames.ORDER_REFUND_STATUS_QUEUE);
    }

    @Bean
    public Binding refundRequestedBinding(Queue orderRefundStatusQueue, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(orderRefundStatusQueue).to(foodrescueEvents).with(MessagingNames.REFUND_REQUESTED_EVENT);
    }

    @Bean
    public Binding refundCompletedBinding(Queue orderRefundStatusQueue, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(orderRefundStatusQueue).to(foodrescueEvents).with(MessagingNames.REFUND_COMPLETED_EVENT);
    }

    // 3. Batch Cancelled Queue & Bindings
    @Bean
    public Queue orderBatchCancelledQueue() {
        return QueueBuilder.durable(MessagingNames.ORDER_BATCH_CANCELLED_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", MessagingNames.DEAD_LETTER_EXCHANGE,
                        "x-dead-letter-routing-key", MessagingNames.ORDER_BATCH_CANCELLED_QUEUE
                ))
                .build();
    }

    @Bean
    public Queue orderBatchCancelledDeadQueue() {
        return QueueBuilder.durable(MessagingNames.ORDER_BATCH_CANCELLED_DEAD_QUEUE).build();
    }

    @Bean
    public Binding dlqBatchCancelledBinding(Queue orderBatchCancelledDeadQueue, DirectExchange foodrescueDlx) {
        return BindingBuilder.bind(orderBatchCancelledDeadQueue).to(foodrescueDlx).with(MessagingNames.ORDER_BATCH_CANCELLED_QUEUE);
    }

    @Bean
    public Binding batchCancelledBinding(Queue orderBatchCancelledQueue, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(orderBatchCancelledQueue).to(foodrescueEvents).with(MessagingNames.CATALOG_BATCH_CANCELLED_EVENT);
    }
}
