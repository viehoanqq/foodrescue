package com.foodrescue.finance.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class FinanceModels {
    private FinanceModels() {}

    public record Intent(
            Long orderId,
            String orderCode,
            Long customerId,
            Long storeId,
            BigDecimal totalAmount,
            String status,
            String paymentStatus,
            LocalDateTime paymentExpiresAt) {}

    public record Account(
            Long storeId,
            String status,
            String bankName,
            String bankAccountNo,
            String bankAccountName) {}

    public record Link(
            long paymentId, long orderId, String txnRef, String paymentUrl, String status) {}

    public record PayoutRequest(long storeId, LocalDate from, LocalDate to) {}

    public record Callback(
            String txnRef,
            BigDecimal amount,
            String responseCode,
            String transactionStatus,
            String gatewayTxnNo,
            String bankCode,
            LocalDateTime paidAt) {
        public boolean success() {
            return "00".equals(responseCode) && "00".equals(transactionStatus);
        }
    }
}
