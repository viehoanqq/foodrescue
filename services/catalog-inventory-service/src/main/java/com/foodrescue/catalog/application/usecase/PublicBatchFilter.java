package com.foodrescue.catalog.application.usecase;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PublicBatchFilter(Long storeId, Long categoryId, String keyword, BigDecimal minPrice,
                                BigDecimal maxPrice, LocalDateTime pickupStartAfter,
                                LocalDateTime pickupEndBefore, BigDecimal latitude,
                                BigDecimal longitude, BigDecimal radiusKm, String sort) {}
