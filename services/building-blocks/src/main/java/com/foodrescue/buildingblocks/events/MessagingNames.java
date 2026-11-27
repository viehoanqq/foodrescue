package com.foodrescue.buildingblocks.events;

/** Shared RabbitMQ names used by publishers, bindings and consumers. */
public final class MessagingNames {
    private MessagingNames() {}

    public static final String EVENTS_EXCHANGE = "foodrescue.events";
    public static final String MIGRATION_QUEUE = "foodrescue.migration-events";
    public static final String ALL_EVENTS_ROUTING_KEY = "#";
    public static final String DEAD_LETTER_EXCHANGE = "foodrescue.dlx";

    public static final String AUDIT_ACTION_EVENT = "audit.action.v1";
    public static final String STORE_STATUS_CHANGED_EVENT = "store.status-changed.v1";
    public static final String ORDER_STATE_CHANGED_EVENT = "order.state-changed.v1";
    public static final String ORDER_REFUND_REQUIRED_EVENT = "order.refund-required.v1";
    public static final String PAYMENT_SUCCEEDED_EVENT = "payment.succeeded.v1";
    public static final String PAYMENT_FAILED_EVENT = "payment.failed.v1";
    public static final String REFUND_REQUESTED_EVENT = "refund.requested.v1";
    public static final String REFUND_COMPLETED_EVENT = "refund.completed.v1";
    public static final String CATALOG_BATCH_CANCELLED_EVENT = "catalog.batch-cancelled.v1";

    public static final String ORDER_PAYMENT_RESULTS_QUEUE = "order.payment-results";
    public static final String ORDER_PAYMENT_RESULTS_DEAD_QUEUE = "order.payment-results.dead";
    public static final String ORDER_REFUND_STATUS_QUEUE = "order.refund-status";
    public static final String ORDER_REFUND_STATUS_DEAD_QUEUE = "order.refund-status.dead";
    public static final String ORDER_BATCH_CANCELLED_QUEUE = "order.batch-cancelled";
    public static final String ORDER_BATCH_CANCELLED_DEAD_QUEUE = "order.batch-cancelled.dead";
    public static final String IDENTITY_STORE_STATUS_QUEUE = "identity.store-status";
    public static final String AUDIT_ACTION_DEAD_ROUTING_KEY = "audit.action.dead";
    public static final String IDENTITY_AUDIT_QUEUE = "identity.audit-events";
    public static final String IDENTITY_AUDIT_DEAD_QUEUE = "identity.audit-dead";
    public static final String IDENTITY_AUDIT_CONSUMER = "identity.audit";
}
