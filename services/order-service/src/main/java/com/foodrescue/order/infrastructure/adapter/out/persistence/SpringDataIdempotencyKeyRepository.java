package com.foodrescue.order.infrastructure.adapter.out.persistence;

import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataIdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, String> {
}
