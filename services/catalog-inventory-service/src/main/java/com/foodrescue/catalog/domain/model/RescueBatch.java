package com.foodrescue.catalog.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RescueBatch(Long id, Long storeId, Long categoryId, String title, String description,
                          String imageUrl, BigDecimal originalPrice, BigDecimal rescuePrice,
                          int quantity, int remainingQuantity, LocalDateTime pickupStart,
                          LocalDateTime pickupEnd, String status, LocalDateTime createdAt,
                          long version, boolean deleted) {}
