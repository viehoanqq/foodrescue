package com.foodrescue.buildingblocks.events;

/** Shared RabbitMQ names used by publishers, bindings and consumers. */
public final class MessagingNames {
    private MessagingNames() {}

    public static final String EVENTS_EXCHANGE = "foodrescue.events";
    public static final String MIGRATION_QUEUE = "foodrescue.migration-events";
    public static final String ALL_EVENTS_ROUTING_KEY = "#";

    public static final String AUDIT_ACTION_EVENT = "audit.action.v1";
    public static final String AUDIT_ACTION_DEAD_ROUTING_KEY = "audit.action.dead";
    public static final String IDENTITY_AUDIT_QUEUE = "identity.audit-events";
    public static final String IDENTITY_AUDIT_DEAD_QUEUE = "identity.audit-dead";
    public static final String IDENTITY_AUDIT_CONSUMER = "identity.audit";
}
