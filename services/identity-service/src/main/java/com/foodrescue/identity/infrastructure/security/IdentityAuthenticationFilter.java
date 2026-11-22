package com.foodrescue.identity.infrastructure.security;

import com.foodrescue.identity.api.IdentityError;
import com.foodrescue.identity.domain.repository.UserRepository;
import com.foodrescue.identity.application.port.out.TokenPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class IdentityAuthenticationFilter extends OncePerRequestFilter {
    private final TokenPort tokens;
    private final UserRepository users;
    private final IdentityError errors;

    public IdentityAuthenticationFilter(TokenPort tokens, UserRepository users, IdentityError errors) {
        this.tokens = tokens;
        this.users = users;
        this.errors = errors;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest req) {
        return "POST".equals(req.getMethod()) && ("/api/auth/login".equals(req.getRequestURI())
                || "/api/auth/register".equals(req.getRequestURI()));
    }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            var token = tokens.read(header.substring(7).trim());
            if (token.isPresent()) {
                var user = users.findById(token.get().userId());
                if (user.isPresent() && user.get().tokenVersion() == token.get().tokenVersion()) {
                    if (!"ACTIVE".equals(user.get().status())) {
                        errors.write(req, res, 403, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
                        return;
                    }
                    if (user.get().mustChangePassword() && !passwordChangeAllowed(req)) {
                        errors.write(req, res, 403, "PASSWORD_CHANGE_REQUIRED", "Vui lòng đổi mật khẩu trước khi tiếp tục");
                        return;
                    }
                    var principal = new IdentityPrincipal(user.get().id(), user.get().role(),
                            user.get().mustChangePassword());
                    var auth = new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.get().role())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(req, res);
    }

    private boolean passwordChangeAllowed(HttpServletRequest req) {
        return ("GET".equals(req.getMethod()) && "/api/me".equals(req.getRequestURI()))
                || ("PUT".equals(req.getMethod()) && "/api/me/password".equals(req.getRequestURI()));
    }
}
