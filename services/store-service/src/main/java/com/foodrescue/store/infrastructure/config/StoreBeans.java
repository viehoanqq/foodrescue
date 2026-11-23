package com.foodrescue.store.infrastructure.config;

import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.application.port.out.IImageStorage;
import com.foodrescue.store.application.port.out.IStaffIdentityPort;
import com.foodrescue.store.application.usecase.CreateStaffUseCase;
import com.foodrescue.store.application.usecase.RegisterStoreUseCase;
import com.foodrescue.store.application.usecase.ReadStoreUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StoreBeans {
    @Bean
    CreateStaffUseCase createStaffUseCase(IStoreRepository stores, IStaffIdentityPort identity) {
        return new CreateStaffUseCase(stores, identity);
    }

    @Bean
    RegisterStoreUseCase registerStoreUseCase(IStoreRepository stores, IImageStorage images) {
        return new RegisterStoreUseCase(stores, images);
    }

    @Bean
    ReadStoreUseCase readStoreUseCase(IStoreRepository stores) {
        return new ReadStoreUseCase(stores);
    }
}
