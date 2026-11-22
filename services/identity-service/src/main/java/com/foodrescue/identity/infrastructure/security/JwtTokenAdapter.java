package com.foodrescue.identity.infrastructure.security;

import com.foodrescue.identity.application.port.out.TokenPort;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenAdapter implements TokenPort {
    private final SecretKey key;
    private final Duration lifetime;

    public JwtTokenAdapter(@Value("${app.jwt.secret}") String secret,
                           @Value("${app.jwt.expiration-hours}") long hours) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        lifetime = Duration.ofHours(hours);
    }

    @Override public String issue(Long userId, long version) {
        Instant now = Instant.now();
        return Jwts.builder().subject(userId.toString()).claim("ver", version)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(lifetime)))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    @Override public Optional<TokenIdentity> read(String token) {
        try {
            var signed = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            if (!"HS256".equals(signed.getHeader().getAlgorithm())) return Optional.empty();
            var claims = signed.getPayload();
            Object version = claims.get("ver");
            if (claims.getExpiration() == null || claims.getSubject() == null
                    || !(version instanceof Number number)) return Optional.empty();
            long userId = Long.parseLong(claims.getSubject());
            if (userId <= 0 || number.longValue() < 0) return Optional.empty();
            return Optional.of(new TokenIdentity(userId, number.longValue()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
