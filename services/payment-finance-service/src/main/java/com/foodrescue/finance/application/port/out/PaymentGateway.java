package com.foodrescue.finance.application.port.out;

import com.foodrescue.finance.domain.model.FinanceModels.*;

import java.time.LocalDateTime;
import java.util.Map;

public interface PaymentGateway {
    String url(
            Intent intent, String txnRef, LocalDateTime created, LocalDateTime expires, String ip);

    Callback verify(Map<String, String> params);
}
