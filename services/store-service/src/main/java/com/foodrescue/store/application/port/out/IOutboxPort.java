package com.foodrescue.store.application.port.out;

import com.foodrescue.buildingblocks.events.EventEnvelope;

/** Called inside the same transaction that changes a Store aggregate. */
public interface IOutboxPort {
    void append(EventEnvelope<?> event);
}
