package com.foodrescue.catalog.infrastructure.messaging;

/** Tên queue do Catalog sở hữu (queue là của service nhận). */
public final class CatalogMessaging {
    private CatalogMessaging() {}

    public static final String STORE_STATUS_QUEUE = "catalog.store-status";
    public static final String STORE_STATUS_DEAD_QUEUE = "catalog.store-status.dead";
    public static final String STORE_STATUS_DLQ_ROUTING_KEY = STORE_STATUS_QUEUE;
}
