package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.foodrescue.catalog.application.port.out.IInboxPort;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.InboxEventEntity;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.InboxEventId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaInboxAdapter implements IInboxPort {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markProcessed(String consumerName, UUID eventId) {
        if (entityManager.find(InboxEventEntity.class, new InboxEventId(consumerName, eventId)) != null) {
            return false;
        }
        // Hai bản giao đồng thời cùng eventId: bản thua vi phạm PK, rollback rồi nhận lại và thấy đã xử lý.
        entityManager.persist(new InboxEventEntity(consumerName, eventId, Instant.now()));
        return true;
    }
}
