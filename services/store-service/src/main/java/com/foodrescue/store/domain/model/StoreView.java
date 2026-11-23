package com.foodrescue.store.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class StoreView {
    private StoreView() {}

    public record Registration(Long storeId, String status, String rejectReason, LocalDateTime createdAt,
                               String name, String description, String phone, String address,
                               BigDecimal latitude, BigDecimal longitude, String imageUrl,
                               LocalTime openTime, LocalTime closeTime, String bankName,
                               String bankAccountNoMasked, String bankAccountName) {}
    public record Profile(Long id, Long ownerId, String name, String description, String phone,
                          String address, BigDecimal latitude, BigDecimal longitude, String imageUrl,
                          LocalTime openTime, LocalTime closeTime, String status, String rejectReason,
                          BigDecimal commissionRate, String bankName, String bankAccountNoMasked,
                          String bankAccountName, LocalDateTime createdAt) {}
    public record Summary(Long id, String name, String address, String imageUrl,
                          BigDecimal latitude, BigDecimal longitude, BigDecimal rating,
                          long reviewCount, Double distanceKm) {}
    public record Detail(Long id, String name, String description, String phone, String address,
                         String imageUrl, BigDecimal latitude, BigDecimal longitude,
                         BigDecimal rating, long reviewCount, LocalTime openTime, LocalTime closeTime) {}
    public record Page(List<Summary> content, int page, int size, long totalElements, long totalPages) {}
    public record Admin(Long id, Long ownerId, String name, String description, String phone,
                        String address, BigDecimal latitude, BigDecimal longitude, String imageUrl,
                        LocalTime openTime, LocalTime closeTime, String status, String rejectReason,
                        BigDecimal commissionRate, String bankName, String bankAccountNoMasked,
                        String bankAccountName, LocalDateTime createdAt, String licenseImageUrl) {}
    public record AdminPage(List<Admin> content, int page, int size, long totalElements, long totalPages) {}
}
