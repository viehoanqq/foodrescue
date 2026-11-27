package com.foodrescue.identity.domain.model;

import com.foodrescue.identity.domain.model.enums.UserRole;
import com.foodrescue.identity.domain.model.enums.UserStatus;
import java.time.LocalDateTime;
import java.math.BigDecimal;

public record UserAccount(Long id, String email, String passwordHash, String fullName,
                          String phone, UserRole role, Long storeId, UserStatus status,
                          boolean mustChangePassword, LocalDateTime createdAt, long tokenVersion,
                          String address, BigDecimal latitude, BigDecimal longitude, String googleSub) {
    public UserAccount(Long id, String email, String passwordHash, String fullName,
                       String phone, UserRole role, Long storeId, UserStatus status,
                       boolean mustChangePassword, LocalDateTime createdAt, long tokenVersion,
                       String address, BigDecimal latitude, BigDecimal longitude) {
        this(id, email, passwordHash, fullName, phone, role, storeId, status,
                mustChangePassword, createdAt, tokenVersion, address, latitude, longitude, null);
    }
    public UserAccount(Long id, String email, String passwordHash, String fullName, String phone,
                       UserRole role, Long storeId, UserStatus status, boolean mustChangePassword,
                       LocalDateTime createdAt, long tokenVersion) {
        this(id, email, passwordHash, fullName, phone, role, storeId, status,
                mustChangePassword, createdAt, tokenVersion, null, null, null);
    }
}
