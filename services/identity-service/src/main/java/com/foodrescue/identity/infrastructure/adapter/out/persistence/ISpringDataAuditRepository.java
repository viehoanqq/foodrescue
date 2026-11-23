package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ISpringDataAuditRepository extends JpaRepository<AuditLogEntity, Long> {
}
