package com.foodrescue.identity.infrastructure.config;

import com.foodrescue.identity.api.IdentityError;
import com.foodrescue.identity.application.port.out.ITokenPort;
import com.foodrescue.identity.domain.repository.IUserRepository;
import com.foodrescue.identity.infrastructure.security.IdentityAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class IdentitySecurity {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ITokenPort tokens, IUserRepository users,
                                           IdentityError errors) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable()).formLogin(f -> f.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/register/otp", "/api/auth/login", "/api/auth/google").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/internal/health/db", "/actuator/health", "/internal/users/**", "/internal/staff", "/internal/staff/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/me", "/api/me/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, 401,
                                "UNAUTHORIZED", "Vui lòng đăng nhập"))
                        .accessDeniedHandler((req, res, ex) -> errors.write(req, res, 403,
                                "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này")))
                .addFilterBefore(new IdentityAuthenticationFilter(tokens, users, errors),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
