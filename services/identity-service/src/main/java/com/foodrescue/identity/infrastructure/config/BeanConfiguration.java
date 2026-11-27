package com.foodrescue.identity.infrastructure.config;

import com.foodrescue.identity.application.port.out.IAuditRepository;
import com.foodrescue.identity.application.port.out.IPasswordPort;
import com.foodrescue.identity.application.port.out.ITokenPort;
import com.foodrescue.identity.application.port.out.IUnitOfWork;
import com.foodrescue.identity.application.port.out.IEmailOtpPort;
import com.foodrescue.identity.application.port.out.IGoogleIdentityPort;
import com.foodrescue.identity.application.usecase.IdentityService;
import com.foodrescue.identity.domain.repository.IUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {
    @Bean
    IdentityService identityService(IUserRepository users, IPasswordPort passwords, ITokenPort tokens,
                                    IAuditRepository audit, IUnitOfWork unitOfWork, IGoogleIdentityPort google,
                                    IEmailOtpPort emailOtp) {
        return new IdentityService(users, passwords, tokens, audit, unitOfWork, google, emailOtp);
    }
}
