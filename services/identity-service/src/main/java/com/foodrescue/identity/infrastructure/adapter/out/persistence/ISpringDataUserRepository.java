package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ISpringDataUserRepository extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    Optional<UserEntity> findByGoogleSub(String googleSub);
    long countByRoleAndStatus(String role, String status);
    Page<UserEntity> findByRoleAndStoreId(String role, Long storeId, Pageable pageable);

    @Modifying
    @Query("UPDATE UserEntity u SET u.tokenVersion = u.tokenVersion + 1 "
            + "WHERE u.role = 'STORE_STAFF' AND u.storeId = :storeId")
    int incrementStaffTokenVersions(@Param("storeId") Long storeId);
}
