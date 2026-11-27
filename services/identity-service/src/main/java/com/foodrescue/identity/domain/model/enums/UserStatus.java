package com.foodrescue.identity.domain.model.enums;

import java.util.Locale;
import java.util.Optional;

/** Trạng thái hoạt động của tài khoản người dùng (users.status). */
public enum UserStatus {
    ACTIVE,
    LOCKED;

    public boolean isActive() {
        return this == ACTIVE;
    }

    public static UserStatus fromLocked(boolean locked) {
        return locked ? LOCKED : ACTIVE;
    }

    public static Optional<UserStatus> fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(UserStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
    }
}
