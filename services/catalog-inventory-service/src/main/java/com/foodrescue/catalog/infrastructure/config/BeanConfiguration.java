package com.foodrescue.catalog.infrastructure.config;

import com.foodrescue.catalog.application.port.out.ReservationPort;
import com.foodrescue.catalog.application.usecase.ReservationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {
    @Bean
    ReservationService reservationService(ReservationPort reservations) {
        return new ReservationService(reservations);
    }
}
