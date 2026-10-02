package com.foodrescue.common.security;

import com.foodrescue.common.enums.UserStatus;
import com.foodrescue.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Chạy ở mọi request: xác định người đang gọi API.
 * 1. Header "Authorization: Bearer <token>" -> id người dùng trong token.
 * 2. Nếu bật DevAuth (chỉ máy dev): header "X-Dev-User-Id: 8" -> giả lập người dùng id 8.
 * Sau đó đọc người dùng từ CSDL: bị khoá thì trả 403 ACCOUNT_LOCKED; đang dùng mật khẩu tạm thì chỉ
 * cho gọi GET /api/me và PUT /api/me/password.
 * Không có hoặc sai token thì để trống, Spring Security tự trả 401 với API cần đăng nhập.
 *
 * KHÔNG đánh dấu @Component: SecurityConfig tự tạo, tránh Spring Boot đăng ký filter chạy 2 lần.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String DEV_HEADER = "X-Dev-User-Id";

    private final JwtService jwtService;
    private final AuthUserLoader authUserLoader;
    private final ApiErrorWriter errorWriter;
    private final boolean devAuthEnabled;

    public JwtAuthFilter(JwtService jwtService, AuthUserLoader authUserLoader, ApiErrorWriter errorWriter,
                         boolean devAuthEnabled) {
        this.jwtService = jwtService;
        this.authUserLoader = authUserLoader;
        this.errorWriter = errorWriter;
        this.devAuthEnabled = devAuthEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Optional<Long> userId = resolveUserId(req);
        if (userId.isPresent()) {
            Optional<AuthUserLoader.LoadedUser> loaded = authUserLoader.loadById(userId.get());
            if (loaded.isPresent()) {
                if (loaded.get().status() == UserStatus.LOCKED) {
                    errorWriter.write(req, res, ErrorCode.ACCOUNT_LOCKED);
                    return;
                }
                AuthUser user = loaded.get().user();
                if (user.mustChangePassword() && !isPasswordChangeAllowed(req)) {
                    errorWriter.write(req, res, ErrorCode.PASSWORD_CHANGE_REQUIRED);
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(req, res);
    }

    private Optional<Long> resolveUserId(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return jwtService.parseUserId(header.substring(7).trim());
        }
        String dev = req.getHeader(DEV_HEADER);
        if (devAuthEnabled && dev != null && !dev.isBlank()) {
            try {
                return Optional.of(Long.valueOf(dev.trim()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Tài khoản đang dùng mật khẩu tạm chỉ được xem hồ sơ và đổi mật khẩu. */
    private boolean isPasswordChangeAllowed(HttpServletRequest req) {
        String path = req.getRequestURI();
        return ("GET".equals(req.getMethod()) && "/api/me".equals(path))
                || ("PUT".equals(req.getMethod()) && "/api/me/password".equals(path));
    }
}
