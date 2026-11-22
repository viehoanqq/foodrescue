package com.foodrescue.finance.infrastructure.config;

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
public class FinanceSecurity {
    @Bean
    SecurityFilterChain financeSecurityFilterChain(HttpSecurity http, ObjectMapper mapper,
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
                                "/internal/health/db", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/payments/vnpay/return", "/api/payments/vnpay/ipn").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/store/revenue", "/api/store/statement", "/api/store/payouts", "/api/store/payouts/**")
                            .hasRole("STORE_OWNER")
                        .requestMatchers("/api/payments", "/api/payments/**").hasRole("CUSTOMER")
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
