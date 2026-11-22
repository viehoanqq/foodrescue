package com.foodrescue.identity.infrastructure.security;

public record IdentityPrincipal(Long id, String role, boolean mustChangePassword) {
}
