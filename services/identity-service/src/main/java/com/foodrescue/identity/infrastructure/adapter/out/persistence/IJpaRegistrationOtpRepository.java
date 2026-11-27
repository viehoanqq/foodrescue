package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.RegistrationEmailOtpEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface IJpaRegistrationOtpRepository extends JpaRepository<RegistrationEmailOtpEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RegistrationEmailOtpEntity o where o.email = :email")
    Optional<RegistrationEmailOtpEntity> findLocked(@Param("email") String email);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update RegistrationEmailOtpEntity o set o.attempts = o.attempts + 1 " +
            "where o.email = :email and o.attempts < 5")
    int incrementAttempts(@Param("email") String email);
}
