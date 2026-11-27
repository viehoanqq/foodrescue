package com.foodrescue.store.domain.model;

import java.math.BigDecimal;
import java.time.LocalTime;

public record StoreProfileUpdate(String name, String description, String phone, String address,
                                 BigDecimal latitude, BigDecimal longitude,
                                 LocalTime openTime, LocalTime closeTime,
                                 String bankName, String bankAccountNo, String bankAccountName) {}
