package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.domain.model.enums.UserRole;
import com.foodrescue.identity.domain.model.enums.UserStatus;
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
    long countByRoleAndStatus(UserRole role, UserStatus status);
    Page<UserEntity> findByRoleAndStoreId(UserRole role, Long storeId, Pageable pageable);

    @Modifying
    @Query("UPDATE UserEntity u SET u.tokenVersion = u.tokenVersion + 1 "
            + "WHERE u.role = :role AND u.storeId = :storeId")
    int incrementTokenVersionsByRoleAndStore(@Param("storeId") Long storeId, @Param("role") UserRole role);

    default int incrementStaffTokenVersions(Long storeId) {
        return incrementTokenVersionsByRoleAndStore(storeId, UserRole.STORE_STAFF);
    }
}
