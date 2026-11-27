package com.foodrescue.finance.application.port.out;

import com.foodrescue.finance.domain.model.FinanceModels.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public interface FinanceLedger {
    Link existing(long customer, String key, long order);

    Link createPayment(
            Intent intent,
            String key,
            String txnRef,
            String url,
            LocalDateTime expires,
            UUID correlation);

    Map<String, String> ipn(Callback callback, UUID correlation);

    Map<String, Object> paymentStatus(long order);

    Map<String, Object> returnStatus(Callback callback);

    Map<String, Object> createPayout(
            PayoutRequest request, Account account, long actor, String key, UUID correlation);

    Map<String, Object> completeRefund(long id, String reference, long actor, UUID correlation);

    Map<String, Object> changePayout(
            long id, String action, String reference, long actor, UUID correlation);
}
