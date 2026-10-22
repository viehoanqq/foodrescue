package com.foodrescue.dto.response;

import com.foodrescue.entity.enums.PayoutStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 1 kỳ chi trả (bản rút gọn, không kèm danh sách đơn) cho phía cửa hàng. Cũng là kết quả của POST /api/store/payouts/request.
 * Thông tin ngân hàng là bản chụp lúc tạo kỳ (BR-15). PayoutResponse (có danh sách đơn) do TV1 làm cho phía admin.
 */
public record PayoutSummaryResponse(
        Long id,
        String payoutCode,
        LocalDate periodFrom,
        LocalDate periodTo,
        int orderCount,
        BigDecimal grossAmount,
        BigDecimal commissionAmount,
        BigDecimal netAmount,
        String bankName,
        String bankAccountNo,
        PayoutStatus status,
        String transferRef,
        LocalDateTime createdAt,
        LocalDateTime paidAt) {
}
