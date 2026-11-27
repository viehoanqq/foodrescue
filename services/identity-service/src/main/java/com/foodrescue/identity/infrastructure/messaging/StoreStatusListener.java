package com.foodrescue.identity.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.identity.domain.repository.IUserRepository;
import com.foodrescue.identity.domain.model.UserAccount;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StoreStatusListener {
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final IUserRepository users;

    public StoreStatusListener(ObjectMapper json, JdbcTemplate jdbc, IUserRepository users) {
        this.json = json; this.jdbc = jdbc; this.users = users;
    }

    @RabbitListener(queues = MessagingNames.IDENTITY_STORE_STATUS_QUEUE)
    @Transactional
    public void receive(String message) throws Exception {
        var event = json.readTree(message);
        if (!MessagingNames.STORE_STATUS_CHANGED_EVENT.equals(event.path("eventType").asText()))
            throw new IllegalArgumentException("Unsupported store event");
        UUID eventId = UUID.fromString(event.path("eventId").asText());
        var payload = event.path("payload");
        long storeId = payload.path("storeId").asLong();
        long ownerId = payload.path("ownerId").asLong();
        long version = payload.path("version").asLong();
        String status = payload.path("status").asText();
        if (storeId <= 0 || ownerId <= 0 || version <= 0
                || version != event.path("aggregateVersion").asLong()
                || !"Store".equals(event.path("aggregateType").asText())
                || !Long.toString(storeId).equals(event.path("aggregateId").asText())
                || !java.util.Set.of("APPROVED", "REJECTED", "LOCKED").contains(status))
            throw new IllegalArgumentException("Invalid store status payload");
        int inserted = jdbc.update("INSERT IGNORE INTO inbox_events(consumer_name,event_id,processed_at) VALUES (?,?,?)",
                MessagingNames.IDENTITY_STORE_STATUS_QUEUE, eventId.toString(), Timestamp.valueOf(LocalDateTime.now()));
        if (inserted == 0) return;
        int created = jdbc.update("INSERT IGNORE INTO store_status_projection" +
                        "(store_id,owner_id,status,aggregate_version) VALUES (?,?,?,?)",
                storeId, ownerId, status, version);
        int advanced = created == 0 ? jdbc.update("UPDATE store_status_projection SET owner_id=?,status=?," +
                        "aggregate_version=?,updated_at=CURRENT_TIMESTAMP(6) " +
                        "WHERE store_id=? AND aggregate_version<?",
                ownerId, status, version, storeId, version) : 0;
        if ("REJECTED".equals(status)) return;
        UserAccount old = users.findById(ownerId)
                .orElseThrow(() -> new IllegalStateException("Store owner does not exist"));
        if ("LOCKED".equals(status)) {
            if (created + advanced > 0 && "STORE_OWNER".equals(old.role())) {
                users.save(new UserAccount(old.id(), old.email(), old.passwordHash(), old.fullName(), old.phone(),
                        old.role(), old.storeId(), old.status(), old.mustChangePassword(), old.createdAt(),
                        old.tokenVersion() + 1, old.address(), old.latitude(), old.longitude()));
                jdbc.update("UPDATE users SET token_version=token_version+1 " +
                        "WHERE role='STORE_STAFF' AND store_id=?", storeId);
            }
            return;
        }
        // users.store_id is reserved for STORE_STAFF by the database constraint.
        if ("STORE_OWNER".equals(old.role()) && old.storeId() == null) return;
        if (!"CUSTOMER".equals(old.role()) || !"ACTIVE".equals(old.status()))
            throw new IllegalStateException("Store owner is not an active customer");
        users.save(new UserAccount(old.id(), old.email(), old.passwordHash(), old.fullName(), old.phone(),
                "STORE_OWNER", null, old.status(), old.mustChangePassword(), old.createdAt(),
                old.tokenVersion() + 1, old.address(), old.latitude(), old.longitude()));
    }
}
