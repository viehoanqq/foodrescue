package com.foodrescue.store.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class StoreView {
    private StoreView() {}

    public record Registration(Long storeId, String status, String rejectReason, LocalDateTime createdAt) {}
    public record Summary(Long id, String name, String address, String imageUrl,
                          BigDecimal latitude, BigDecimal longitude, BigDecimal rating,
                          long reviewCount, Double distanceKm) {}
    public record Detail(Long id, String name, String description, String phone, String address,
                         String imageUrl, BigDecimal latitude, BigDecimal longitude,
                         BigDecimal rating, long reviewCount, LocalTime openTime, LocalTime closeTime) {}
    public record Page(List<Summary> content, int page, int size, long totalElements, long totalPages) {}
}
