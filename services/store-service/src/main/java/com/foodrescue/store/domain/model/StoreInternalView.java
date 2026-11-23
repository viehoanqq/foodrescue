package com.foodrescue.store.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class StoreInternalView {
    private StoreInternalView() {}

    public record CheckoutPolicy(Long storeId, String status, BigDecimal commissionRate,
                                 long version, LocalDateTime updatedAt) {}

    public record PayoutAccount(Long storeId, String status, String bankName,
                                String bankAccountNo, String bankAccountName,
                                LocalDateTime updatedAt) {}

    public record Counts(long approved, long locked, long pending) {}
}
