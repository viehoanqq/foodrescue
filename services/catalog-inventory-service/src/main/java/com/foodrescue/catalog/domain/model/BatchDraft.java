package com.foodrescue.catalog.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BatchDraft(Long categoryId, String title, String description, String imageUrl,
                         BigDecimal originalPrice, BigDecimal rescuePrice, Integer quantity,
                         LocalDateTime pickupStart, LocalDateTime pickupEnd) {}
