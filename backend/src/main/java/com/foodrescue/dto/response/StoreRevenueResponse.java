package com.foodrescue.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Báo cáo doanh thu của cửa hàng (GET /api/store/revenue?from&to). Chỉ tính đơn COMPLETED / NO_SHOW
 * (tiền cửa hàng nhận đã cố định, BR-13), theo ngày chốt đơn completedAt.
 */
public record StoreRevenueResponse(
        LocalDate periodFrom,
        LocalDate periodTo,
        int orderCount,
        @Schema(description = "Tổng khách đã trả") BigDecimal grossAmount,
        @Schema(description = "Tổng phí nền tảng") BigDecimal commissionAmount,
        @Schema(description = "Cửa hàng thực nhận = gross - commission") BigDecimal netAmount,
        @Schema(description = "Số đơn chưa thuộc kỳ chi trả nào (payout_id IS NULL)") int unpaidOrderCount,
        @Schema(description = "Số tiền chưa chi; > 0 và không có kỳ PENDING thì được Yêu cầu rút tiền (BR-20)") BigDecimal unpaidNetAmount,
        @Schema(description = "Tổng các kỳ chi trả đang PENDING") BigDecimal pendingPayoutAmount,
        @Schema(description = "Tổng các kỳ chi trả đã PAID") BigDecimal paidPayoutAmount,
        @Schema(description = "Điểm đánh giá trung bình 1-5") BigDecimal ratingAvg,
        int ratingCount,
        @Schema(description = "Số liệu theo ngày để vẽ biểu đồ") List<DailyRevenue> daily) {

    /** 1 điểm của biểu đồ doanh thu theo ngày. */
    public record DailyRevenue(LocalDate date, int orderCount, BigDecimal grossAmount, BigDecimal netAmount) {
    }
}
