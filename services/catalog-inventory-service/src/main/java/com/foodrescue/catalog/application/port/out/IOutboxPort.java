package com.foodrescue.catalog.application.port.out;

import com.foodrescue.buildingblocks.events.EventEnvelope;

public interface IOutboxPort {
    /** Phải gọi trong transaction nghiệp vụ; không publish trực tiếp từ controller. */
    void append(EventEnvelope<?> event);
}
