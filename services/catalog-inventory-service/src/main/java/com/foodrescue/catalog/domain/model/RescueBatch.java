package com.foodrescue.catalog.domain.model;

import com.foodrescue.catalog.domain.model.enums.BatchStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RescueBatch(Long id, Long storeId, Long categoryId, String title, String description,
                          String imageUrl, BigDecimal originalPrice, BigDecimal rescuePrice,
                          int quantity, int remainingQuantity, LocalDateTime pickupStart,
                          LocalDateTime pickupEnd, BatchStatus status, LocalDateTime createdAt,
                          long version, boolean deleted) {}
