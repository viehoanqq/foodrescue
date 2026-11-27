package com.foodrescue.catalog.domain.model;

import java.math.BigDecimal;
import java.time.LocalTime;

public record CatalogStoreSummary(Long id, String name, String description, String phone, String address,
                                  String imageUrl, BigDecimal latitude, BigDecimal longitude,
                                  BigDecimal rating, long reviewCount, Double distanceKm,
                                  LocalTime openTime, LocalTime closeTime) {}
