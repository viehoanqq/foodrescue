package com.foodrescue.catalog.domain.model;

import java.time.LocalDateTime;

public record StockMovement(Long id, Long batchId, int changeQty, String reason, Long orderId,
                           Long actorId, int remainingAfter, String note, LocalDateTime createdAt) {}
