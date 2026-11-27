package com.foodrescue.finance.application.usecase;

import com.foodrescue.finance.application.port.out.*;
import com.foodrescue.finance.domain.exception.FinanceException;
import com.foodrescue.finance.domain.model.FinanceModels.*;

import java.time.*;
import java.util.*;

public class FinanceUseCase {
    private final FinanceDependencies dependencies;
    private final FinanceLedger ledger;
    private final PaymentGateway gateway;
    private final Clock clock;

    public FinanceUseCase(
            FinanceDependencies dependencies,
            FinanceLedger ledger,
            PaymentGateway gateway,
            Clock clock) {
        this.dependencies = dependencies;
        this.ledger = ledger;
        this.gateway = gateway;
        this.clock = clock;
    }

    public Link payment(long customer, long order, String key, String ip, UUID correlation) {
        key(key);
        Link old = ledger.existing(customer, key, order);
        if (old != null) return old;
        Intent intent = dependencies.intent(order, correlation);
        if (intent == null
                || intent.customerId() == null
                || intent.customerId() != customer
                || intent.orderId() != order)
            throw new FinanceException(403, "FORBIDDEN", "Đơn hàng không thuộc tài khoản này");
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        if (!"PENDING_PAYMENT".equals(intent.status())
                || !"UNPAID".equals(intent.paymentStatus())
                || intent.totalAmount() == null
                || intent.totalAmount().signum() <= 0
                || intent.totalAmount().stripTrailingZeros().scale() > 0
                || intent.paymentExpiresAt() == null
                || !intent.paymentExpiresAt().minusMinutes(5).isAfter(now))
            throw FinanceException.conflict("PAYMENT_INTENT_INVALID");
        LocalDateTime expires = intent.paymentExpiresAt().minusMinutes(5);
        if (expires.isAfter(now.plusMinutes(10))) expires = now.plusMinutes(10);
        String ref = UUID.randomUUID().toString().replace("-", "");
        String url = gateway.url(intent, ref, now, expires, ip);
        return ledger.createPayment(intent, key, ref, url, expires, correlation);
    }

    public Map<String, String> ipn(Map<String, String> params, UUID correlation) {
        try {
            return ledger.ipn(gateway.verify(params), correlation);
        } catch (FinanceException e) {
            if (e.code().equals("VNPAY_SIGNATURE_INVALID"))
                return Map.of("RspCode", "97", "Message", "Invalid signature");
            if (e.status() == 400) return Map.of("RspCode", "99", "Message", "Invalid parameters");
            throw e;
        }
    }

    public Map<String, Object> result(Map<String, String> params) {
        return ledger.returnStatus(gateway.verify(params));
    }

    public Map<String, Object> payout(
            PayoutRequest request, long actor, String key, UUID correlation) {
        key(key);
        if (request.storeId() <= 0
                || request.from() == null
                || request.to() == null
                || request.to().isBefore(request.from()))
            throw new FinanceException(400, "VALIDATION_ERROR", "Khoảng ngày không hợp lệ");
        Account account = dependencies.account(request.storeId(), correlation);
        if (account == null
                || account.storeId() != request.storeId()
                || account.bankAccountNo() == null
                || account.bankAccountNo().isBlank()
                || account.bankName() == null
                || account.bankAccountName() == null)
            throw FinanceException.conflict("PAYMENT_INTENT_INVALID");
        return ledger.createPayout(request, account, actor, key, correlation);
    }

    private void key(String key) {
        if (key == null || key.isBlank() || key.length() > 100)
            throw new FinanceException(
                    400, "VALIDATION_ERROR", "Idempotency-Key bắt buộc, tối đa 100 ký tự");
    }
}
