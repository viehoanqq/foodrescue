package com.foodrescue.dto.response;

import com.foodrescue.entity.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 1 dòng sao kê (GET /api/store/statement): từng đơn đã chốt và việc đã được chi trả chưa. */
public record StatementLineResponse(
        Long orderId,
        String orderCode,
        OrderStatus status,
        LocalDateTime completedAt,
        BigDecimal totalAmount,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal storeEarning,
        @Schema(description = "null = chưa chi trả") Long payoutId,
        String payoutCode) {
}
