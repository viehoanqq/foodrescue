package com.foodrescue.store.infrastructure.config;

import com.foodrescue.store.application.port.out.OwnedStorePort;
import com.foodrescue.store.application.port.out.StaffIdentityPort;
import com.foodrescue.store.application.usecase.CreateStaffUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StoreBeans {
    @Bean
    CreateStaffUseCase createStaffUseCase(OwnedStorePort stores, StaffIdentityPort identity) {
        return new CreateStaffUseCase(stores, identity);
    }
}
