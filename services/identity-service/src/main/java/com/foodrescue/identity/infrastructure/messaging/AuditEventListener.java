package com.foodrescue.identity.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** An inbox row and the audit row are committed together; redelivery is safe. */
@Component
public class AuditEventListener {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditEventListener(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @RabbitListener(queues = "identity.audit-events")
    @Transactional
    public void receive(String message) throws Exception {
        JsonNode event = json.readTree(message);
        UUID eventId = UUID.fromString(requiredText(event, "eventId"));
        if (!"audit.action.v1".equals(requiredText(event, "eventType"))) {
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
        int inserted = jdbc.update("INSERT IGNORE INTO inbox_events(consumer_name,event_id,processed_at) VALUES (?,?,?)",
                "identity.audit", eventId.toString(), Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))));
        if (inserted == 0) return;
        jdbc.update("INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,amount,detail,created_at) " +
                        "VALUES (?,?,?,?,?,?,?)", actorId, action, entityType, entityId, amount, detail,
                Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))));
    }

    private String requiredText(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Missing event field: " + key);
        }
        return value.textValue();
    }
}
