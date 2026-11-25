package com.foodrescue.order.application.port.out;

import com.foodrescue.buildingblocks.events.EventEnvelope;

/** Called inside the same database transaction that changes an Order aggregate. */
public interface IOutboxPort {
    void append(EventEnvelope<?> event);
}
