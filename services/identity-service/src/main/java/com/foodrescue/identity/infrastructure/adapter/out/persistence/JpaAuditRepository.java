package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.application.port.out.IAuditRepository;
import com.foodrescue.identity.domain.model.PageResult;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.AuditLogEntity;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAuditRepository implements IAuditRepository {
    private final ISpringDataAuditRepository repository;
    private final NamedParameterJdbcTemplate jdbc;

    public JpaAuditRepository(ISpringDataAuditRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
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

    @Override public PageResult<Map<String, Object>> search(Long actorId, String action, String entityType,
            Long entityId, LocalDateTime from, LocalDateTime to, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (actorId != null) {
            where.append(" AND a.actor_id = :actorId");
            params.addValue("actorId", actorId);
        }
        if (action != null) {
            where.append(" AND a.action = :action");
            params.addValue("action", action);
        }
        if (entityType != null) {
            where.append(" AND a.entity_type = :entityType");
            params.addValue("entityType", entityType);
        }
        if (entityId != null) {
            where.append(" AND a.entity_id = :entityId");
            params.addValue("entityId", entityId);
        }
        if (from != null) {
            where.append(" AND a.created_at >= :fromTime");
            params.addValue("fromTime", Timestamp.valueOf(from));
        }
        if (to != null) {
            where.append(" AND a.created_at <= :toTime");
            params.addValue("toTime", Timestamp.valueOf(to));
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs a" + where, params, Long.class);
        params.addValue("size", size).addValue("offset", (long) page * size);
        var rows = jdbc.query("SELECT a.id, a.actor_id, u.email AS actor_email, a.action, a.entity_type, " +
                        "a.entity_id, a.amount, a.detail, a.created_at FROM audit_logs a " +
                        "LEFT JOIN users u ON u.id = a.actor_id" + where +
                        " ORDER BY a.created_at DESC, a.id DESC LIMIT :size OFFSET :offset",
                params, (rs, index) -> {
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
        });
        return new PageResult<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }
}
