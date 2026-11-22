package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.application.port.out.AuditPort;
import com.foodrescue.identity.domain.model.PageResult;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.AuditLogEntity;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAuditStore implements AuditPort {
    private final AuditJpaRepository repository;
    private final JdbcTemplate jdbc;

    public JpaAuditStore(AuditJpaRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Override public void record(Long actorId, String action, String entityType, Long entityId, String detail) {
        AuditLogEntity entry = new AuditLogEntity();
        entry.setActorId(actorId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetail(detail);
        entry.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        repository.save(entry);
    }

    @Override public PageResult<Map<String, Object>> search(int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs", Long.class);
        var rows = jdbc.query("SELECT a.id, a.actor_id, u.email AS actor_email, a.action, a.entity_type, " +
                        "a.entity_id, a.amount, a.detail, a.created_at FROM audit_logs a " +
                        "LEFT JOIN users u ON u.id = a.actor_id ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?",
                (rs, index) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("actorId", rs.getObject("actor_id"));
            row.put("actorEmail", rs.getString("actor_email"));
            row.put("action", rs.getString("action"));
            row.put("entityType", rs.getString("entity_type"));
            row.put("entityId", rs.getLong("entity_id"));
            row.put("amount", rs.getBigDecimal("amount"));
            row.put("detail", rs.getString("detail"));
            row.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime());
            return row;
        }, size, (long) page * size);
        return new PageResult<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }
}
