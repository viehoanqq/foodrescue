package com.foodrescue.catalog.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.catalog.application.usecase.StoreStatusProjectionUseCase;
import com.foodrescue.catalog.domain.model.StoreStatusChange;
import com.foodrescue.catalog.domain.model.enums.StoreProjectionStatus;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Nhận store.status-changed.v1 từ Store; tin sai định dạng đi DLQ, lỗi tạm thời được giao lại. */
@Component
public class StoreStatusConsumer {
    private final ObjectMapper json;
    private final StoreStatusProjectionUseCase projection;

    public StoreStatusConsumer(ObjectMapper json, StoreStatusProjectionUseCase projection) {
        this.json = json;
        this.projection = projection;
    }

    @RabbitListener(queues = CatalogMessaging.STORE_STATUS_QUEUE)
    public void receive(String message) {
        UUID eventId;
        StoreStatusChange change;
        try {
            JsonNode event = json.readTree(message);
            if (!MessagingNames.STORE_STATUS_CHANGED_EVENT.equals(event.path("eventType").asText())) {
                throw new IllegalArgumentException("Unsupported store event");
            }
            eventId = UUID.fromString(event.path("eventId").asText());
            JsonNode payload = event.path("payload");
            long storeId = payload.path("storeId").asLong();
            long version = payload.path("version").asLong();
            if (!"Store".equals(event.path("aggregateType").asText())
                    || !Long.toString(storeId).equals(event.path("aggregateId").asText())
                    || version != event.path("aggregateVersion").asLong()) {
                throw new IllegalArgumentException("Store event metadata does not match payload");
            }
            change = new StoreStatusChange(storeId, payload.path("ownerId").asLong(),
                    StoreProjectionStatus.parseRequired(payload.path("status").asText()), version);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new AmqpRejectAndDontRequeueException("Invalid store status event", ex);
        }
        projection.apply(eventId, change);
    }
}
