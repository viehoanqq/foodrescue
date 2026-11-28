package com.foodrescue.catalog.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.catalog.application.usecase.StoreStatusProjectionUseCase;
import com.foodrescue.catalog.domain.model.StoreStatusChange;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

class StoreStatusConsumerTest {
    private final StoreStatusProjectionUseCase useCase = mock(StoreStatusProjectionUseCase.class);
    private final StoreStatusConsumer consumer = new StoreStatusConsumer(new ObjectMapper(), useCase);
    private final UUID eventId = UUID.fromString("22b50d8d-cfe3-487a-bd6d-8538fda2d7e1");

    private String message(String eventType, String aggregateId, int aggregateVersion, String status, int version) {
        return """
                {"eventId":"%s","eventType":"%s","schemaVersion":1,"aggregateType":"Store",
                 "aggregateId":"%s","aggregateVersion":%d,
                 "payload":{"storeId":15,"ownerId":7,"status":"%s","version":%d}}
                """.formatted(eventId, eventType, aggregateId, aggregateVersion, status, version);
    }

    @Test
    void validEventIsForwardedToUseCase() {
        consumer.receive(message("store.status-changed.v1", "15", 2, "APPROVED", 2));

        verify(useCase).apply(eventId, new StoreStatusChange(15L, 7L, "APPROVED", 2L));
    }

    @Test
    void malformedOrMismatchedEventsGoToDeadLetterQueue() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.receive("not json"));
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.receive(message("order.state-changed.v1", "15", 2, "APPROVED", 2)));
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.receive(message("store.status-changed.v1", "99", 2, "APPROVED", 2)));
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.receive(message("store.status-changed.v1", "15", 3, "APPROVED", 2)));
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.receive(message("store.status-changed.v1", "15", 2, "PENDING", 2)));
        verify(useCase, never()).apply(any(), any());
    }
}
