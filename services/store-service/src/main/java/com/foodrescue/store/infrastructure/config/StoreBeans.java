package com.foodrescue.store.infrastructure.config;

import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.application.port.out.IAdminStoreRepository;
import com.foodrescue.store.application.port.out.IInternalStoreRepository;
import com.foodrescue.store.application.port.out.IImageStorage;
import com.foodrescue.store.application.port.out.IStaffIdentityPort;
import com.foodrescue.store.application.port.out.IReviewRepository;
import com.foodrescue.store.application.port.out.IOrderEligibilityPort;
import com.foodrescue.store.application.usecase.StoreStaffUseCase;
import com.foodrescue.store.application.usecase.RegisterStoreUseCase;
import com.foodrescue.store.application.usecase.ReadStoreUseCase;
import com.foodrescue.store.application.usecase.ReadAdminStoreUseCase;
import com.foodrescue.store.application.usecase.ReadInternalStoreUseCase;
import com.foodrescue.store.application.usecase.AdminStoreUseCase;
import com.foodrescue.store.application.usecase.StoreReviewUseCase;
import com.foodrescue.store.application.usecase.UpdateStoreProfileUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StoreBeans {
    @Bean
    ReadInternalStoreUseCase readInternalStoreUseCase(IInternalStoreRepository stores) {
        return new ReadInternalStoreUseCase(stores);
    }
    @Bean
    UpdateStoreProfileUseCase updateStoreProfileUseCase(IStoreRepository stores) {
        return new UpdateStoreProfileUseCase(stores);
    }
    @Bean
    StoreReviewUseCase storeReviewUseCase(IStoreRepository stores, IReviewRepository reviews,
                                         IOrderEligibilityPort orders) {
        return new StoreReviewUseCase(stores, reviews, orders);
    }
    @Bean
    StoreStaffUseCase storeStaffUseCase(IStoreRepository stores, IStaffIdentityPort identity) {
        return new StoreStaffUseCase(stores, identity);
    }

    @Bean
    RegisterStoreUseCase registerStoreUseCase(IStoreRepository stores, IImageStorage images) {
        return new RegisterStoreUseCase(stores, images);
    }

    @Bean
    ReadStoreUseCase readStoreUseCase(IStoreRepository stores) {
        return new ReadStoreUseCase(stores);
    }

    @Bean
    ReadAdminStoreUseCase readAdminStoreUseCase(IAdminStoreRepository stores) {
        return new ReadAdminStoreUseCase(stores);
    }

    @Bean
    AdminStoreUseCase adminStoreUseCase(IAdminStoreRepository stores) {
        return new AdminStoreUseCase(stores);
    }
}
