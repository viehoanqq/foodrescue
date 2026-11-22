package com.foodrescue.identity.infrastructure.config;

import com.foodrescue.identity.application.port.out.AuditPort;
import com.foodrescue.identity.application.port.out.PasswordPort;
import com.foodrescue.identity.application.port.out.TokenPort;
import com.foodrescue.identity.application.port.out.UnitOfWork;
import com.foodrescue.identity.application.usecase.IdentityService;
import com.foodrescue.identity.domain.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {
    @Bean
    IdentityService identityService(UserRepository users, PasswordPort passwords, TokenPort tokens,
                                    AuditPort audit, UnitOfWork unitOfWork) {
        return new IdentityService(users, passwords, tokens, audit, unitOfWork);
    }
}
