package com.foodrescue.buildingblocks.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BearerUserFilterTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private String token(long version, Instant expiresAt) {
        return Jwts.builder().subject("42").claim("ver", version)
                .issuedAt(Date.from(Instant.now())).expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void usesCurrentRoleAndNameFromIdentity() throws Exception {
        var identity = mock(IdentityStatusClient.class);
        when(identity.find(42L)).thenReturn(new IdentityStatusClient.UserStatus(
                42L, "ACTIVE", "STORE_OWNER", "Chủ cửa hàng", null, 3, false));
        var filter = new BearerUserFilter(new AccessTokenVerifier(SECRET), identity,
                new ApiAuthErrorWriter(new ObjectMapper().findAndRegisterModules()));
        var request = new MockHttpServletRequest("GET", "/api/store/profile");
        request.addHeader("Authorization", "Bearer " + token(3, Instant.now().plusSeconds(60)));
        var response = new MockHttpServletResponse();
        var seen = new AtomicReference<AuthenticatedUser>();

        filter.doFilter(request, response, (req, res) -> {
            seen.set((AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        });

        assertEquals(42L, seen.get().id());
        assertEquals("STORE_OWNER", seen.get().role());
        assertEquals("Chủ cửa hàng", seen.get().fullName());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void rejectsStaleVersionBeforeBusinessCodeRuns() throws Exception {
        var identity = mock(IdentityStatusClient.class);
        when(identity.find(42L)).thenReturn(new IdentityStatusClient.UserStatus(
                42L, "ACTIVE", "CUSTOMER", "Khách", null, 4, false));
        var filter = new BearerUserFilter(new AccessTokenVerifier(SECRET), identity,
                new ApiAuthErrorWriter(new ObjectMapper().findAndRegisterModules()));
        var request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer " + token(3, Instant.now().plusSeconds(60)));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> fail("Business code must not run"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
    }

    @Test
    void rejectsExpiredTokenWithoutCallingIdentity() throws Exception {
        var identity = mock(IdentityStatusClient.class);
        var filter = new BearerUserFilter(new AccessTokenVerifier(SECRET), identity,
                new ApiAuthErrorWriter(new ObjectMapper().findAndRegisterModules()));
        var request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer " + token(3, Instant.now().minusSeconds(60)));
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> fail("Business code must not run"));

        assertEquals(401, response.getStatus());
        verifyNoInteractions(identity);
    }
}
