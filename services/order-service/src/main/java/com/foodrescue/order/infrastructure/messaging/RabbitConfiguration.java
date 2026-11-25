package com.foodrescue.order.infrastructure.messaging;

import com.foodrescue.buildingblocks.events.MessagingNames;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitConfiguration {
    public static final String EVENTS_EXCHANGE = MessagingNames.EVENTS_EXCHANGE;
    public static final String DLX_EXCHANGE = "foodrescue.dlx";

    public static final String ORDER_PAYMENT_RESULTS_QUEUE = "order.payment-results";
    public static final String ORDER_PAYMENT_RESULTS_DEAD_QUEUE = "order.payment-results.dead";

    public static final String ORDER_REFUND_STATUS_QUEUE = "order.refund-status";
    public static final String ORDER_REFUND_STATUS_DEAD_QUEUE = "order.refund-status.dead";

    public static final String ORDER_BATCH_CANCELLED_QUEUE = "order.batch-cancelled";
    public static final String ORDER_BATCH_CANCELLED_DEAD_QUEUE = "order.batch-cancelled.dead";

    @Bean
    public TopicExchange foodrescueEvents() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange foodrescueDlx() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    // 1. Payment Results Queue & Bindings
    @Bean
    public Queue orderPaymentResultsQueue() {
        return QueueBuilder.durable(ORDER_PAYMENT_RESULTS_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", DLX_EXCHANGE,
                        "x-dead-letter-routing-key", ORDER_PAYMENT_RESULTS_QUEUE
                ))
                .build();
    }

    @Bean
    public Queue orderPaymentResultsDeadQueue() {
        return QueueBuilder.durable(ORDER_PAYMENT_RESULTS_DEAD_QUEUE).build();
    }

    @Bean
    public Binding dlqPaymentBinding(Queue orderPaymentResultsDeadQueue, DirectExchange foodrescueDlx) {
        return BindingBuilder.bind(orderPaymentResultsDeadQueue).to(foodrescueDlx).with(ORDER_PAYMENT_RESULTS_QUEUE);
    }

    @Bean
    public Binding paymentSucceededBinding(Queue orderPaymentResultsQueue, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(orderPaymentResultsQueue).to(foodrescueEvents).with("payment.succeeded.v1");
    }

    @Bean
    public Binding paymentFailedBinding(Queue orderPaymentResultsQueue, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(orderPaymentResultsQueue).to(foodrescueEvents).with("payment.failed.v1");
    }

    // 2. Refund Status Queue & Bindings
    @Bean
    public Queue orderRefundStatusQueue() {
        return QueueBuilder.durable(ORDER_REFUND_STATUS_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", DLX_EXCHANGE,
                        "x-dead-letter-routing-key", ORDER_REFUND_STATUS_QUEUE
                ))
                .build();
    }

    @Bean
    public Queue orderRefundStatusDeadQueue() {
        return QueueBuilder.durable(ORDER_REFUND_STATUS_DEAD_QUEUE).build();
    }

    @Bean
    public Binding dlqRefundBinding(Queue orderRefundStatusDeadQueue, DirectExchange foodrescueDlx) {
        return BindingBuilder.bind(orderRefundStatusDeadQueue).to(foodrescueDlx).with(ORDER_REFUND_STATUS_QUEUE);
    }

    @Bean
    public Binding refundRequestedBinding(Queue orderRefundStatusQueue, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(orderRefundStatusQueue).to(foodrescueEvents).with("refund.requested.v1");
    }

    @Bean
    public Binding refundCompletedBinding(Queue orderRefundStatusQueue, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(orderRefundStatusQueue).to(foodrescueEvents).with("refund.completed.v1");
    }

    // 3. Batch Cancelled Queue & Bindings
    @Bean
    public Queue orderBatchCancelledQueue() {
        return QueueBuilder.durable(ORDER_BATCH_CANCELLED_QUEUE)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", DLX_EXCHANGE,
                        "x-dead-letter-routing-key", ORDER_BATCH_CANCELLED_QUEUE
                ))
                .build();
    }

    @Bean
    public Queue orderBatchCancelledDeadQueue() {
        return QueueBuilder.durable(ORDER_BATCH_CANCELLED_DEAD_QUEUE).build();
    }

    @Bean
    public Binding dlqBatchCancelledBinding(Queue orderBatchCancelledDeadQueue, DirectExchange foodrescueDlx) {
        return BindingBuilder.bind(orderBatchCancelledDeadQueue).to(foodrescueDlx).with(ORDER_BATCH_CANCELLED_QUEUE);
    }

    @Bean
    public Binding batchCancelledBinding(Queue orderBatchCancelledQueue, TopicExchange foodrescueEvents) {
        return BindingBuilder.bind(orderBatchCancelledQueue).to(foodrescueEvents).with("catalog.batch-cancelled.v1");
    }
}
