package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.StoreStatusProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ISpringDataStoreStatusRepository extends JpaRepository<StoreStatusProjectionEntity, Long> {
    @Modifying
    @Query(value = "INSERT IGNORE INTO store_status_projection "
            + "(store_id,owner_id,status,aggregate_version) VALUES (:storeId,:ownerId,:status,:version)", nativeQuery = true)
    int insertIfAbsent(@Param("storeId") Long storeId, @Param("ownerId") Long ownerId,
                       @Param("status") String status, @Param("version") Long version);

    @Modifying
    @Query(value = "UPDATE store_status_projection SET owner_id=:ownerId,status=:status," 
            + "aggregate_version=:version,updated_at=CURRENT_TIMESTAMP(6) "
            + "WHERE store_id=:storeId AND aggregate_version<:version", nativeQuery = true)
    int advanceIfNewer(@Param("storeId") Long storeId, @Param("ownerId") Long ownerId,
                       @Param("status") String status, @Param("version") Long version);
}
