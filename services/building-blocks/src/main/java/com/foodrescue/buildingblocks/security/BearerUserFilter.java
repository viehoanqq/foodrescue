package com.foodrescue.buildingblocks.security;

import com.foodrescue.buildingblocks.http.InternalServiceException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates browser API requests without trusting caller-supplied user headers or IDs. */
public final class BearerUserFilter extends OncePerRequestFilter {
    private final AccessTokenVerifier tokens;
    private final IdentityStatusClient identity;
    private final ApiAuthErrorWriter errors;

    public BearerUserFilter(AccessTokenVerifier tokens, IdentityStatusClient identity,
                            ApiAuthErrorWriter errors) {
        this.tokens = tokens;
        this.identity = identity;
        this.errors = errors;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        if (!header.startsWith("Bearer ")) {
            errors.write(request, response, 401, "UNAUTHORIZED", "Access token không hợp lệ");
            return;
        }
        var token = tokens.verify(header.substring(7).trim());
        if (token.isEmpty()) {
            errors.write(request, response, 401, "UNAUTHORIZED", "Access token không hợp lệ hoặc đã hết hạn");
            return;
        }
        IdentityStatusClient.UserStatus user;
        try {
            user = identity.find(token.get().userId());
        } catch (InternalServiceException ex) {
            if (ex.status() == 404) {
                errors.write(request, response, 401, "UNAUTHORIZED", "Tài khoản không tồn tại");
            } else {
                errors.write(request, response, 503, "IDENTITY_UNAVAILABLE", "Không kiểm tra được tài khoản");
            }
            return;
        } catch (RestClientException ex) {
            errors.write(request, response, 503, "IDENTITY_UNAVAILABLE", "Không kết nối được Identity");
            return;
        }
        if (user == null || !token.get().userId().equals(user.userId()) || user.role() == null) {
            errors.write(request, response, 503, "IDENTITY_UNAVAILABLE", "Phản hồi Identity không hợp lệ");
            return;
        }
        if (user.tokenVersion() != token.get().tokenVersion()) {
            errors.write(request, response, 401, "UNAUTHORIZED", "Phiên đăng nhập đã hết hiệu lực");
            return;
        }
        if (!"ACTIVE".equals(user.status())) {
            errors.write(request, response, 403, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
            return;
        }
        if (user.mustChangePassword()) {
            errors.write(request, response, 403, "PASSWORD_CHANGE_REQUIRED", "Vui lòng đổi mật khẩu trước khi tiếp tục");
            return;
        }
        var principal = new AuthenticatedUser(user.userId(), user.role(), user.fullName(), user.storeId());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
