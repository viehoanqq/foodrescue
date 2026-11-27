package com.foodrescue.catalog.application.port.out;

import java.util.UUID;

public interface IInboxPort {
    /** Ghi dấu đã xử lý trong cùng transaction; false nếu event này đã xử lý trước đó. */
    boolean markProcessed(String consumerName, UUID eventId);
}
