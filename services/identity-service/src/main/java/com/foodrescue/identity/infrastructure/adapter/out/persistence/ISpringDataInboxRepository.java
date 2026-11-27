package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.InboxEventEntity;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ISpringDataInboxRepository extends JpaRepository<InboxEventEntity, InboxEventEntity.Key> {
    @Modifying
    @Query(value = "INSERT IGNORE INTO inbox_events(consumer_name,event_id,processed_at) "
            + "VALUES (:consumerName,:eventId,:processedAt)", nativeQuery = true)
    int insertIfAbsent(@Param("consumerName") String consumerName,
                       @Param("eventId") String eventId,
                       @Param("processedAt") LocalDateTime processedAt);
}
