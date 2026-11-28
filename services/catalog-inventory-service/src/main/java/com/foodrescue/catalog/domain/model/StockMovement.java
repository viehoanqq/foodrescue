package com.foodrescue.catalog.domain.model;

import com.foodrescue.catalog.domain.model.enums.StockMovementReason;
import java.time.LocalDateTime;

public record StockMovement(Long id, Long batchId, int changeQty, StockMovementReason reason, Long orderId,
                           Long actorId, int remainingAfter, String note, LocalDateTime createdAt) {}
