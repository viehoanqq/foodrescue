package com.foodrescue.store.application.usecase;

import com.foodrescue.store.application.port.out.IImageStorage;
import com.foodrescue.store.application.port.out.IImageStorage.Upload;
import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreRegistration;
import com.foodrescue.store.domain.model.enums.StoreStatus;
import java.time.LocalDateTime;

public class RegisterStoreUseCase {
    private final IStoreRepository stores;
    private final IImageStorage images;

    public RegisterStoreUseCase(IStoreRepository stores, IImageStorage images) {
        this.stores = stores;
        this.images = images;
    }

    public Result execute(Long ownerId, StoreRegistration data, Upload license, Upload image) {
        validate(data, license);
        String licenseUrl = null;
        String imageUrl = null;
        try {
            licenseUrl = images.saveLicense(license);
            if (image != null && !image.isEmpty()) imageUrl = images.saveImage(image);
            var saved = stores.save(ownerId, data, licenseUrl, imageUrl);
            return new Result(saved.storeId(), ownerId, StoreStatus.PENDING.name(), saved.createdAt());
        } catch (RuntimeException ex) {
            images.delete(licenseUrl);
            images.delete(imageUrl);
            throw ex;
        }
    }

    private void validate(StoreRegistration d, Upload license) {
        if (blank(d.storeName(), 150) || blank(d.storePhone(), 20) || blank(d.address(), 255)
                || blank(d.bankName(), 100) || blank(d.bankAccountNo(), 30)
                || blank(d.bankAccountName(), 100) || d.latitude() == null || d.longitude() == null
                || d.latitude().compareTo(java.math.BigDecimal.valueOf(-90)) < 0
                || d.latitude().compareTo(java.math.BigDecimal.valueOf(90)) > 0
                || d.longitude().compareTo(java.math.BigDecimal.valueOf(-180)) < 0
                || d.longitude().compareTo(java.math.BigDecimal.valueOf(180)) > 0
                || d.openTime() == null || d.closeTime() == null)
            throw new StoreException(400, "VALIDATION_ERROR", "Dữ liệu đăng ký không hợp lệ");
        if (!d.closeTime().isAfter(d.openTime()))
            throw new StoreException(400, "INVALID_TIME", "Giờ đóng cửa phải sau giờ mở cửa");
        if (license == null || license.isEmpty())
            throw new StoreException(400, "LICENSE_REQUIRED", "Vui lòng tải ảnh giấy phép");
    }

    private boolean blank(String value, int max) {
        return value == null || value.isBlank() || value.length() > max;
    }

    public record Result(Long storeId, Long ownerId, String status, LocalDateTime createdAt) {}
}
