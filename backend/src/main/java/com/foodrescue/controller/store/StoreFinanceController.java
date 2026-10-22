package com.foodrescue.controller.store;

import com.foodrescue.controller.mock.StoreMockData;
import com.foodrescue.dto.response.PageResponse;
import com.foodrescue.dto.response.PayoutSummaryResponse;
import com.foodrescue.dto.response.StatementLineResponse;
import com.foodrescue.dto.response.StoreRevenueResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * H2 (dữ liệu giả): doanh thu, sao kê, kỳ chi trả và yêu cầu rút tiền của cửa hàng. Chỉ STORE_OWNER.
 * Dữ liệu mẫu theo cửa hàng Cơm Tấm 365; khớp công thức chi trả (gross - commission = net).
 * LƯU Ý: POST /payouts/request sẽ gọi PayoutService.createForStore(storeId, from, to) của TV1 (việc A6, tuần 6).
 */
@RestController
@RequestMapping("/api/store")
@PreAuthorize("hasRole('STORE_OWNER')")
public class StoreFinanceController {

    @GetMapping("/revenue")
    @Operation(tags = StoreApiTags.FINANCE, summary = "Báo cáo doanh thu",
            description = "Số đơn, doanh thu, phí nền tảng, thực nhận và số tiền chưa chi, tính trên đơn COMPLETED / NO_SHOW "
                    + "theo ngày chốt đơn. from, to dạng yyyy-MM-dd; bỏ trống = toàn bộ.")
    @ApiResponse(responseCode = "200", description = "StoreRevenueResponse")
    public StoreRevenueResponse getRevenue(
            @Parameter(description = "Từ ngày (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Đến ngày (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return new StoreRevenueResponse(
                from == null ? LocalDate.parse("2026-09-21") : from, to == null ? LocalDate.parse("2026-09-25") : to,
                3, BigDecimal.valueOf(250000), BigDecimal.valueOf(37500), BigDecimal.valueOf(212500),
                1, BigDecimal.valueOf(42500), BigDecimal.ZERO, BigDecimal.valueOf(170000),
                new BigDecimal("4.7"), 38,
                List.of(
                        new StoreRevenueResponse.DailyRevenue(LocalDate.parse("2026-09-23"), 1, BigDecimal.valueOf(100000), BigDecimal.valueOf(85000)),
                        new StoreRevenueResponse.DailyRevenue(LocalDate.parse("2026-09-24"), 1, BigDecimal.valueOf(100000), BigDecimal.valueOf(85000)),
                        new StoreRevenueResponse.DailyRevenue(LocalDate.parse("2026-09-25"), 1, BigDecimal.valueOf(50000), BigDecimal.valueOf(42500))));
    }

    @GetMapping("/statement")
    @Operation(tags = StoreApiTags.FINANCE, summary = "Sao kê từng đơn",
            description = "Mỗi dòng là 1 đơn đã chốt kèm tiền cửa hàng nhận và kỳ chi trả chứa nó (payoutId = null nghĩa là chưa chi).")
    @ApiResponse(responseCode = "200", description = "PageResponse<StatementLineResponse>")
    public PageResponse<StatementLineResponse> listStatement(
            @Parameter(description = "Từ ngày (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Đến ngày (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<StatementLineResponse> rows = StoreMockData.statement().stream()
                .filter(l -> from == null || !l.completedAt().toLocalDate().isBefore(from))
                .filter(l -> to == null || !l.completedAt().toLocalDate().isAfter(to))
                .toList();
        return PageResponse.ofList(rows, page, size);
    }

    @GetMapping("/payouts")
    @Operation(tags = StoreApiTags.FINANCE, summary = "Danh sách kỳ chi trả của cửa hàng",
            description = "Các kỳ PENDING, PAID, CANCELLED, mới nhất trước. Thông tin ngân hàng là bản chụp lúc tạo kỳ.")
    @ApiResponse(responseCode = "200", description = "Mảng PayoutSummaryResponse")
    public List<PayoutSummaryResponse> listPayouts() {
        return StoreMockData.payouts();
    }

    @PostMapping("/payouts/request")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(tags = StoreApiTags.FINANCE, summary = "Yêu cầu rút tiền",
            description = "Gom toàn bộ đơn chưa chi tới hôm nay thành 1 kỳ PENDING gửi admin (BR-20). Điều kiện: có ít nhất 1 đơn "
                    + "chưa chi và không có kỳ nào đang PENDING. Bấm lần 2 khi còn kỳ PENDING bị chặn (TC19).")
    @ApiResponse(responseCode = "201", description = "Kỳ chi trả vừa tạo (PENDING)")
    @ApiResponse(responseCode = "409", description = "PAYOUT_PENDING_EXISTS: đang có kỳ chi trả chờ xử lý")
    @ApiResponse(responseCode = "422", description = "PAYOUT_NO_ORDERS: không có đơn nào đủ điều kiện")
    public PayoutSummaryResponse requestPayout() {
        return StoreMockData.requestedPayout();
    }
}
