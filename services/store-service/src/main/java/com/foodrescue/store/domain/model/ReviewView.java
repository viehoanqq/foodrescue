package com.foodrescue.store.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ReviewView {
    private ReviewView() {}

    public record Create(Long orderId, Integer rating, String comment) {}
    public record Item(Long id, Long orderId, int rating, String comment, LocalDateTime createdAt) {}
    public record Page(List<Item> content, BigDecimal averageRating, int page,
                       int size, long totalElements, long totalPages) {}
}
