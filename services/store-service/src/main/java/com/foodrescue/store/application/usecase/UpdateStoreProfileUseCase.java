package com.foodrescue.store.application.usecase;

import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreProfileUpdate;
import com.foodrescue.store.domain.model.StoreView;
import java.math.BigDecimal;
import java.util.Set;

public class UpdateStoreProfileUseCase {
    private final IStoreRepository stores;

    public UpdateStoreProfileUseCase(IStoreRepository stores) { this.stores = stores; }

    public StoreView.Profile execute(Long ownerId, StoreProfileUpdate input) {
        validate(input);
        StoreView.Profile current = stores.findProfileByOwnerId(ownerId)
                .orElseThrow(() -> new StoreException(404, "STORE_NOT_FOUND", "Bạn chưa có cửa hàng"));
        if (!Set.of("APPROVED", "LOCKED").contains(current.status()))
            throw new StoreException(409, "STORE_NOT_EDITABLE", "Cửa hàng chưa thể chỉnh sửa hồ sơ");
        return stores.updateProfile(ownerId, new StoreProfileUpdate(
                input.name().trim(), input.description() == null ? null : input.description().trim(),
                input.phone().trim(), input.address().trim(), input.latitude(), input.longitude(),
                input.openTime(), input.closeTime(), input.bankName().trim(),
                input.bankAccountNo() == null || input.bankAccountNo().isBlank()
                        ? null : input.bankAccountNo().trim(), input.bankAccountName().trim()));
    }

    private void validate(StoreProfileUpdate input) {
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
        if (input.bankAccountNo() != null && !input.bankAccountNo().isBlank()
                && !input.bankAccountNo().trim().matches("[0-9]{6,30}"))
            throw new StoreException(400, "INVALID_BANK_ACCOUNT", "Số tài khoản phải gồm 6–30 chữ số");
    }

    private boolean invalid(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }
}
