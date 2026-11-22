package com.foodrescue.buildingblocks.security;

import com.foodrescue.buildingblocks.http.InternalServiceClient;
import java.net.URI;
import java.util.UUID;

/** Calls Identity once per authenticated API request for current role and revocation state. */
public final class IdentityStatusClient {
    private final InternalServiceClient client;

    public IdentityStatusClient(String baseUrl, String internalKey) {
        this.client = new InternalServiceClient(URI.create(baseUrl), internalKey);
    }

    public UserStatus find(Long userId) {
        return client.get("/internal/users/" + userId + "/status", UUID.randomUUID(), UserStatus.class);
    }

    public record UserStatus(Long userId, String status, String role, String fullName, Long storeId,
                             long tokenVersion, boolean mustChangePassword) {}
}
