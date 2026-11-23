package com.foodrescue.store.application.usecase;

import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreProfileUpdate;
import java.math.BigDecimal;

final class StoreProfileUpdates {
    private StoreProfileUpdates() {}

    static StoreProfileUpdate validateAndNormalize(StoreProfileUpdate input) {
        if (input == null || invalid(input.name(), 150) || invalid(input.phone(), 20)
                || invalid(input.address(), 255) || invalid(input.bankName(), 100)
                || invalid(input.bankAccountName(), 100)
                || input.description() != null && input.description().length() > 1000
                || input.latitude() == null || input.longitude() == null
                || input.latitude().compareTo(BigDecimal.valueOf(-90)) < 0
                || input.latitude().compareTo(BigDecimal.valueOf(90)) > 0
                || input.longitude().compareTo(BigDecimal.valueOf(-180)) < 0
                || input.longitude().compareTo(BigDecimal.valueOf(180)) > 0
                || input.openTime() == null || input.closeTime() == null)
            throw new StoreException(400, "VALIDATION_ERROR", "Thông tin cửa hàng không hợp lệ");
        if (!input.closeTime().isAfter(input.openTime()))
            throw new StoreException(400, "INVALID_TIME", "Giờ đóng cửa phải sau giờ mở cửa");
        String account = input.bankAccountNo() == null || input.bankAccountNo().isBlank()
                ? null : input.bankAccountNo().trim();
        if (account != null && !account.matches("[0-9]{6,30}"))
            throw new StoreException(400, "INVALID_BANK_ACCOUNT", "Số tài khoản phải gồm 6–30 chữ số");
        return new StoreProfileUpdate(input.name().trim(),
                input.description() == null ? null : input.description().trim(),
                input.phone().trim(), input.address().trim(), input.latitude(), input.longitude(),
                input.openTime(), input.closeTime(), input.bankName().trim(), account,
                input.bankAccountName().trim());
    }

    private static boolean invalid(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }
}
