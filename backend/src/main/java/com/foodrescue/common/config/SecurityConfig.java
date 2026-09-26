package com.foodrescue.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.common.api.ApiError;
import com.foodrescue.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Khung bảo mật ban đầu. TV1 bổ sung JWT, DevAuth và quy tắc theo vai trò ở việc F4. */
@Configuration
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/ping", "/api/auth/**", "/api/payments/vnpay/ipn",
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/uploads/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/batches/**", "/api/stores/**", "/api/categories/**").permitAll()
                // F4: thêm quy tắc theo vai trò cho /api/cart, /api/orders, /api/store, /api/admin
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> writeError(req, res, ErrorCode.UNAUTHORIZED))
                .accessDeniedHandler((req, res, ex) -> writeError(req, res, ErrorCode.FORBIDDEN)));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Lỗi 401/403 cũng trả đúng định dạng ApiError như mọi lỗi khác. */
    private void writeError(HttpServletRequest req, HttpServletResponse res, ErrorCode code) throws IOException {
        res.setStatus(code.status());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        ApiError body = new ApiError(code.name(), code.defaultMessage(), List.of(), LocalDateTime.now(),
                req.getRequestURI());
        objectMapper.writeValue(res.getOutputStream(), body);
    }
}
