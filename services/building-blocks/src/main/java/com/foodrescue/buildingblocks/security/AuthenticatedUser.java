package com.foodrescue.buildingblocks.security;

/** Identity supplied by a verified access token and the current Identity user status. */
public record AuthenticatedUser(Long id, String role, String fullName, Long storeId) {
}
