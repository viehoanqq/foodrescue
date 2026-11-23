package com.foodrescue.identity.application.port.out;

import java.util.Optional;

public interface ITokenPort {
    String issue(Long userId, long tokenVersion);
    Optional<TokenIdentity> read(String token);

    record TokenIdentity(Long userId, long tokenVersion) {}
}
