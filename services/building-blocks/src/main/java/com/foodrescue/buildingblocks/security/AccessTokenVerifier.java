package com.foodrescue.buildingblocks.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.SecretKey;

/** Verifies Identity's signed JWT locally; role and account status come from Identity. */
public final class AccessTokenVerifier {
    private final SecretKey key;

    public AccessTokenVerifier(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<TokenIdentity> verify(String token) {
        try {
            var signed = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            if (!"HS256".equals(signed.getHeader().getAlgorithm())) return Optional.empty();
            var claims = signed.getPayload();
            if (claims.getExpiration() == null || claims.getSubject() == null) return Optional.empty();
            Object rawVersion = claims.get("ver");
            if (!(rawVersion instanceof Number version)) return Optional.empty();
            long userId = Long.parseLong(claims.getSubject());
            if (userId <= 0 || version.longValue() < 0) return Optional.empty();
            return Optional.of(new TokenIdentity(userId, version.longValue()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record TokenIdentity(Long userId, long tokenVersion) {}
}
