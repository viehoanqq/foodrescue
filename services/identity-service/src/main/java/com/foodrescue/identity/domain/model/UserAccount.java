package com.foodrescue.identity.domain.model;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public record UserAccount(Long id, String email, String passwordHash, String fullName,
                          String phone, String role, Long storeId, String status,
                          boolean mustChangePassword, LocalDateTime createdAt, long tokenVersion,
                          String address, BigDecimal latitude, BigDecimal longitude) {
    public UserAccount(Long id, String email, String passwordHash, String fullName, String phone,
                       String role, Long storeId, String status, boolean mustChangePassword,
                       LocalDateTime createdAt, long tokenVersion) {
        this(id, email, passwordHash, fullName, phone, role, storeId, status,
                mustChangePassword, createdAt, tokenVersion, null, null, null);
    }
}
