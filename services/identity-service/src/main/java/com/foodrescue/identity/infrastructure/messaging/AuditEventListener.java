package com.foodrescue.identity.infrastructure.messaging;

import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.ISpringDataAuditRepository;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.ISpringDataInboxRepository;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.AuditLogEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** An inbox row and the audit row are committed together; redelivery is safe. */
@Component
public class AuditEventListener {
    private final ISpringDataInboxRepository inbox;
    private final ISpringDataAuditRepository audit;
    private final ObjectMapper json;

    public AuditEventListener(ISpringDataInboxRepository inbox, ISpringDataAuditRepository audit, ObjectMapper json) {
        this.inbox = inbox;
        this.audit = audit;
        this.json = json;
    }

    @RabbitListener(queues = MessagingNames.IDENTITY_AUDIT_QUEUE)
    @Transactional
    public void receive(String message) throws Exception {
        JsonNode event = json.readTree(message);
        UUID eventId = UUID.fromString(requiredText(event, "eventId"));
        if (!MessagingNames.AUDIT_ACTION_EVENT.equals(requiredText(event, "eventType"))) {
            throw new IllegalArgumentException("Unsupported audit event type");
        }
        JsonNode payload = event.path("payload");
        String action = requiredText(payload, "action");
        String entityType = requiredText(payload, "entityType");
        long entityId = payload.path("entityId").longValue();
        if (entityId <= 0 || action.length() > 40 || entityType.length() > 20) {
            throw new IllegalArgumentException("Invalid audit action payload");
        }
        Long actorId = event.path("actorId").isNumber() ? event.path("actorId").longValue() : null;
        BigDecimal amount = payload.path("amount").isNumber() ? payload.path("amount").decimalValue() : null;
        String detail = payload.path("detail").isTextual() ? payload.path("detail").textValue() : null;
        if (detail != null && detail.length() > 500) throw new IllegalArgumentException("Audit detail too long");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        int inserted = inbox.insertIfAbsent(MessagingNames.IDENTITY_AUDIT_CONSUMER, eventId.toString(), now);
        if (inserted == 0) return;
        AuditLogEntity entry = new AuditLogEntity();
        entry.setActorId(actorId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAmount(amount);
        entry.setDetail(detail);
        entry.setCreatedAt(now);
        audit.save(entry);
    }

    private String requiredText(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Missing event field: " + key);
        }
        return value.textValue();
    }
}
