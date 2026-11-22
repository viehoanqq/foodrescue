package com.foodrescue.identity.domain.model;

import java.time.LocalDateTime;

public record UserAccount(Long id, String email, String passwordHash, String fullName,
                          String phone, String role, Long storeId, String status,
                          boolean mustChangePassword, LocalDateTime createdAt, long tokenVersion) {
}
