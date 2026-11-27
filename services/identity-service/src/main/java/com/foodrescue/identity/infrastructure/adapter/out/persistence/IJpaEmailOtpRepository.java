package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.EmailOtpEntity;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.EmailOtpId;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface IJpaEmailOtpRepository extends JpaRepository<EmailOtpEntity, EmailOtpId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from EmailOtpEntity o where o.id.otpType = :otpType and o.id.email = :email")
    Optional<EmailOtpEntity> findLocked(@Param("otpType") String otpType, @Param("email") String email);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update EmailOtpEntity o set o.attempts = o.attempts + 1 " +
            "where o.id.otpType = :otpType and o.id.email = :email and o.attempts < 5")
    int incrementAttempts(@Param("otpType") String otpType, @Param("email") String email);
}
