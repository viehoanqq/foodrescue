package com.foodrescue.order.application.port.out;

import java.util.UUID;

public interface IInboxPort {
    boolean claim(String consumerName, UUID eventId);
    boolean isProcessed(String consumerName, UUID eventId);
    void record(String consumerName, UUID eventId);
}
