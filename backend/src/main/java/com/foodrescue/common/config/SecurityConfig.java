package com.foodrescue.common.config;

import com.foodrescue.common.exception.ErrorCode;
import com.foodrescue.common.security.ApiErrorWriter;
import com.foodrescue.common.security.AuthUserLoader;
import com.foodrescue.common.security.JwtAuthFilter;
import com.foodrescue.common.security.JwtService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Bảo mật (F4). Quyền theo khu vực URL (Quy ước chung mục 5.1): đặt API đúng tiền tố
 * thì KHÔNG cần sửa file này. Quyền chi tiết hơn (vd chỉ chủ cửa hàng) dùng @PreAuthorize ở controller.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtService jwtService;
    private final AuthUserLoader authUserLoader;
    private final ApiErrorWriter errorWriter;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.dev-auth.enabled:false}")
    private boolean devAuthEnabled;

    public SecurityConfig(JwtService jwtService, AuthUserLoader authUserLoader, ApiErrorWriter errorWriter) {
        this.jwtService = jwtService;
        this.authUserLoader = authUserLoader;
        this.errorWriter = errorWriter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (devAuthEnabled) {
            log.warn("DevAuth DANG BAT: header {} gia lap nguoi dung. Chi dung tren may dev!", JwtAuthFilter.DEV_HEADER);
        }
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .authorizeHttpRequests(auth -> auth
                // Công khai
                .requestMatchers("/api/ping", "/api/auth/**", "/api/payments/vnpay/ipn", "/error",
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/uploads/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/batches/**", "/api/stores/**", "/api/categories/**").permitAll()
                // Theo vai trò
                .requestMatchers("/api/cart/**", "/api/orders/**", "/api/payments/**").hasRole("CUSTOMER")
                .requestMatchers("/api/store/**").hasAnyRole("STORE_OWNER", "STORE_STAFF")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Còn lại (vd /api/me, POST đánh giá): chỉ cần đăng nhập
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> errorWriter.write(req, res, ErrorCode.UNAUTHORIZED))
                .accessDeniedHandler((req, res, ex) -> errorWriter.write(req, res, ErrorCode.FORBIDDEN)))
            .addFilterBefore(new JwtAuthFilter(jwtService, authUserLoader, errorWriter, devAuthEnabled),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // cost 10, khớp mật khẩu mẫu 123456 trong foodrescue_schema.sql
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
}
