package com.foodrescue.service;

import com.foodrescue.entity.AuditLog;
import com.foodrescue.entity.User;
import com.foodrescue.entity.enums.AuditAction;
import com.foodrescue.entity.enums.EntityType;
import com.foodrescue.repository.AuditLogRepository;
import com.foodrescue.repository.UserRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi nhật ký thao tác audit_logs (chỉ thêm, CSDL có trigger chặn sửa / xoá).
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog log(Long actorId, AuditAction action, EntityType entityType, Long entityId, BigDecimal amount, String detail) {
        AuditLog log = new AuditLog();
        if (actorId != null) {
            User actor = userRepository.findById(actorId).orElse(null);
            log.setActor(actor);
        }
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAmount(amount);
        log.setDetail(detail);
        return auditLogRepository.save(log);
    }
}
