package com.foodrescue.order.infrastructure.config;

import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IOrderReviewRepository;
import com.foodrescue.order.application.usecase.CheckReviewEligibilityUseCase;
import com.foodrescue.order.application.usecase.StockReservationUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {
    @Bean
    CheckReviewEligibilityUseCase checkReviewEligibilityUseCase(IOrderReviewRepository orders) {
        return new CheckReviewEligibilityUseCase(orders);
    }
    @Bean
    StockReservationUseCase stockReservationUseCase(IInventoryPort inventory) {
        return new StockReservationUseCase(inventory);
    }
}
