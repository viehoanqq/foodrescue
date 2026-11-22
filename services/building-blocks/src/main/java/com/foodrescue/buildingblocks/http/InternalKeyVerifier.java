package com.foodrescue.buildingblocks.http;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Development-only service authentication. Replace with service JWT or mTLS before deployment. */
public final class InternalKeyVerifier {
    private final byte[] expected;

    public InternalKeyVerifier(String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("Internal API key is required");
        expected = secret.getBytes(StandardCharsets.UTF_8);
    }

    public void verify(String actual) {
        if (actual == null || !MessageDigest.isEqual(expected, actual.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }
}
