package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.application.port.out.IAuditRepository;
import com.foodrescue.identity.domain.model.PageResult;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.AuditLogEntity;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAuditRepository implements IAuditRepository {
    private final ISpringDataAuditRepository repository;
    private final ISpringDataUserRepository users;

    public JpaAuditRepository(ISpringDataAuditRepository repository, ISpringDataUserRepository users) {
        this.repository = repository;
        this.users = users;
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
        Specification<AuditLogEntity> filters = (root, query, cb) -> cb.conjunction();
        if (actorId != null) filters = filters.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId));
        if (action != null) filters = filters.and((root, query, cb) -> cb.equal(root.get("action"), action));
        if (entityType != null) filters = filters.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        if (entityId != null) filters = filters.and((root, query, cb) -> cb.equal(root.get("entityId"), entityId));
        if (from != null) filters = filters.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        if (to != null) filters = filters.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        Page<AuditLogEntity> found = repository.findAll(filters,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        Set<Long> actorIds = found.getContent().stream().map(AuditLogEntity::getActorId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, String> actorEmails = users.findAllById(actorIds).stream()
                .collect(Collectors.toMap(user -> user.getId(), user -> user.getEmail()));
        List<Map<String, Object>> rows = found.getContent().stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", entry.getId());
            row.put("actorId", entry.getActorId());
            row.put("actorEmail", actorEmails.get(entry.getActorId()));
            row.put("action", entry.getAction());
            row.put("entityType", entry.getEntityType());
            row.put("entityId", entry.getEntityId());
            row.put("amount", entry.getAmount());
            row.put("detail", entry.getDetail());
            row.put("createdAt", entry.getCreatedAt());
            return row;
        }).toList();
        return new PageResult<>(rows, page, size, found.getTotalElements(), found.getTotalPages());
    }
}
