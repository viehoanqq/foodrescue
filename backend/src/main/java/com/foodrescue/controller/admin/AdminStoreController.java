package com.foodrescue.controller.admin;

import com.foodrescue.controller.mock.StoreMockData;
import com.foodrescue.controller.store.StoreApiTags;
import com.foodrescue.dto.request.ApproveStoreRequest;
import com.foodrescue.dto.request.LockStoreRequest;
import com.foodrescue.dto.request.RejectStoreRequest;
import com.foodrescue.dto.request.UpdateStoreCommissionRequest;
import com.foodrescue.dto.response.PageResponse;
import com.foodrescue.dto.response.StoreResponse;
import com.foodrescue.dto.response.StoreSummaryResponse;
import com.foodrescue.entity.enums.StoreStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** H2 (dữ liệu giả): admin duyệt, từ chối, khoá cửa hàng và đặt phí nền tảng. Quyền ADMIN do SecurityConfig (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/stores")
public class AdminStoreController {

    @GetMapping
    @Operation(tags = StoreApiTags.ADMIN_STORES, summary = "Danh sách cửa hàng (lọc theo trạng thái)",
            description = "status: PENDING, APPROVED, REJECTED, LOCKED; bỏ trống = tất cả. Mỗi dòng có ảnh giấy phép VSATTP "
                    + "và toạ độ để admin duyệt ngay trên danh sách.")
    @ApiResponse(responseCode = "200", description = "PageResponse<StoreSummaryResponse>")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: không phải ADMIN")
    public PageResponse<StoreSummaryResponse> listStores(
            @Parameter(description = "Lọc theo trạng thái") @RequestParam(required = false) StoreStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<StoreSummaryResponse> rows = StoreMockData.STORES.stream()
                .filter(s -> status == null || s.status() == status)
                .map(StoreMockData::toSummary)
                .toList();
        return PageResponse.ofList(rows, page, size);
    }

    @PostMapping("/{id}/approve")
    @Operation(tags = StoreApiTags.ADMIN_STORE_ACTIONS, summary = "Duyệt cửa hàng",
            description = "Chỉ duyệt được cửa hàng PENDING. Body tuỳ chọn: commissionRate (0-50%); bỏ trống giữ tỉ lệ hiện có "
                    + "(mặc định 15%). Ghi nhật ký STORE_APPROVED.")
    @ApiResponse(responseCode = "200", description = "Cửa hàng sau khi duyệt (APPROVED)")
    @ApiResponse(responseCode = "404", description = "NOT_FOUND")
    public StoreResponse approveStore(@PathVariable Long id, @Valid @RequestBody(required = false) ApproveStoreRequest body) {
        StoreResponse store = StoreMockData.store(id);
        return StoreMockData.withDecision(store, StoreStatus.APPROVED, null, body == null ? null : body.commissionRate());
    }

    @PostMapping("/{id}/reject")
    @Operation(tags = StoreApiTags.ADMIN_STORE_ACTIONS, summary = "Từ chối cửa hàng",
            description = "Chỉ từ chối được cửa hàng PENDING; bắt buộc nhập lý do. Ghi nhật ký STORE_REJECTED.")
    @ApiResponse(responseCode = "200", description = "Cửa hàng sau khi từ chối (REJECTED, có rejectReason)")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: thiếu lý do")
    @ApiResponse(responseCode = "404", description = "NOT_FOUND")
    public StoreResponse rejectStore(@PathVariable Long id, @Valid @RequestBody RejectStoreRequest body) {
        return StoreMockData.withDecision(StoreMockData.store(id), StoreStatus.REJECTED, body.reason(), null);
    }

    @PostMapping("/{id}/lock")
    @Operation(tags = StoreApiTags.ADMIN_STORE_ACTIONS, summary = "Khoá cửa hàng",
            description = "Túi bị ẩn; đơn CONFIRMED vẫn phải giao hoặc admin huỷ và hoàn; tiền đã bán vẫn chi trả bình thường (BR-25). "
                    + "Ghi nhật ký STORE_LOCKED.")
    @ApiResponse(responseCode = "200", description = "Cửa hàng sau khi khoá (LOCKED)")
    @ApiResponse(responseCode = "404", description = "NOT_FOUND")
    public StoreResponse lockStore(@PathVariable Long id, @Valid @RequestBody(required = false) LockStoreRequest body) {
        return StoreMockData.withDecision(StoreMockData.store(id), StoreStatus.LOCKED, null, null);
    }

    @PostMapping("/{id}/unlock")
    @Operation(tags = StoreApiTags.ADMIN_STORE_ACTIONS, summary = "Mở khoá cửa hàng",
            description = "Đưa cửa hàng LOCKED về APPROVED.")
    @ApiResponse(responseCode = "200", description = "Cửa hàng sau khi mở khoá (APPROVED)")
    @ApiResponse(responseCode = "404", description = "NOT_FOUND")
    public StoreResponse unlockStore(@PathVariable Long id) {
        return StoreMockData.withDecision(StoreMockData.store(id), StoreStatus.APPROVED, null, null);
    }

    @PutMapping("/{id}/commission")
    @Operation(tags = StoreApiTags.ADMIN_COMMISSION, summary = "Đổi tỉ lệ phí nền tảng của cửa hàng",
            description = "commissionRate từ 0 đến 50 (chk_store_rate). Chỉ áp cho đơn đặt SAU khi đổi. Ghi nhật ký STORE_RATE_CHANGED.")
    @ApiResponse(responseCode = "200", description = "Cửa hàng sau khi đổi tỉ lệ phí")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: tỉ lệ ngoài 0-50")
    @ApiResponse(responseCode = "404", description = "NOT_FOUND")
    public StoreResponse updateCommission(@PathVariable Long id, @Valid @RequestBody UpdateStoreCommissionRequest body) {
        StoreResponse store = StoreMockData.store(id);
        return StoreMockData.withDecision(store, store.status(), store.rejectReason(), body.commissionRate());
    }
}
