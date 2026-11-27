package com.foodrescue.finance.infrastructure.config;

import com.foodrescue.finance.application.port.out.*;
import com.foodrescue.finance.application.usecase.FinanceUseCase;
import com.foodrescue.finance.infrastructure.adapter.out.http.*;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

import java.time.Clock;

@Configuration
public class FinanceConfiguration {
    @Bean
    FinanceDependencies financeDependencies(Environment e) {
        return new HttpFinanceDependencies(
                e.getRequiredProperty("app.order-service-url"),
                e.getRequiredProperty("app.store-service-url"),
                e.getRequiredProperty("app.identity-service-url"),
                e.getRequiredProperty("app.internal-api-key"),
                e.getRequiredProperty("app.finance-store-api-key"));
    }

    @Bean
    PaymentGateway paymentGateway(Environment e) {
        return new VnPayGateway(
                e.getProperty("app.vnpay.merchant", ""),
                e.getProperty("app.vnpay.secret", ""),
                e.getRequiredProperty("app.vnpay.url"),
                e.getRequiredProperty("app.vnpay.return-url"));
    }

    @Bean
    FinanceUseCase financeUseCase(
            FinanceDependencies dependencies, FinanceLedger ledger, PaymentGateway gateway) {
        return new FinanceUseCase(dependencies, ledger, gateway, Clock.systemUTC());
    }
}
