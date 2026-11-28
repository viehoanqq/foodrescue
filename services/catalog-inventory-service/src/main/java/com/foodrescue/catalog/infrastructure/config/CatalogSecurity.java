package com.foodrescue.catalog.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.security.AccessTokenVerifier;
import com.foodrescue.buildingblocks.security.ApiAuthErrorWriter;
import com.foodrescue.buildingblocks.security.BearerUserFilter;
import com.foodrescue.buildingblocks.security.IdentityStatusClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class CatalogSecurity {
    @Bean
    SecurityFilterChain catalogSecurityFilterChain(HttpSecurity http, ObjectMapper mapper,
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.identity-service-url}") String identityUrl,
            @Value("${app.internal-api-key}") String internalKey) throws Exception {
        var errors = new ApiAuthErrorWriter(mapper);
        var filter = new BearerUserFilter(new AccessTokenVerifier(jwtSecret),
                new IdentityStatusClient(identityUrl, internalKey), errors);
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable()).formLogin(f -> f.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
                                "/internal/health/db", "/actuator/health", "/internal/reservations", "/internal/reservations/**",
                                "/internal/batches/checkout-store").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**",
                                "/api/batches", "/api/batches/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/store/categories", "/api/store/categories/**",
                                "/api/store/batches", "/api/store/batches",
                                "/api/store/batches/*/stock-movements")
                                .hasAnyRole("STORE_OWNER", "STORE_STAFF")
                        .requestMatchers(HttpMethod.PATCH, "/api/store/batches/*/quantity")
                                .hasAnyRole("STORE_OWNER", "STORE_STAFF")
                        .requestMatchers("/api/store/categories", "/api/store/categories/**",
                                "/api/store/batches", "/api/store/batches/**").hasRole("STORE_OWNER")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, 401,
                                "UNAUTHORIZED", "Vui lòng đăng nhập"))
                        .accessDeniedHandler((req, res, ex) -> errors.write(req, res, 403,
                                "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này")))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
