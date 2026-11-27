package com.foodrescue.identity.domain.model.enums;

import java.util.Locale;
import java.util.Optional;

/** Vai trò tài khoản trong hệ thống (users.role). */
public enum UserRole {
    ADMIN,
    STORE_OWNER,
    STORE_STAFF,
    CUSTOMER;

    public boolean canUseGoogle() {
        return this == CUSTOMER || this == STORE_OWNER;
    }

    public static Optional<UserRole> fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(UserRole.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
    }
}
