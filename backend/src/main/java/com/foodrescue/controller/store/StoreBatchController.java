package com.foodrescue.controller.store;

import com.foodrescue.controller.mock.StoreMockData;
import com.foodrescue.dto.request.AdjustBatchQuantityRequest;
import com.foodrescue.dto.request.CancelBatchRequest;
import com.foodrescue.dto.request.CreateBatchRequest;
import com.foodrescue.dto.request.UpdateBatchRequest;
import com.foodrescue.dto.response.CancelBatchResponse;
import com.foodrescue.dto.response.PageResponse;
import com.foodrescue.dto.response.StockMovementResponse;
import com.foodrescue.dto.response.StoreBatchResponse;
import com.foodrescue.entity.enums.BatchStatus;
import com.foodrescue.exception.BusinessException;
import com.foodrescue.exception.ErrorCode;
import com.foodrescue.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * H2 (dữ liệu giả): túi giải cứu, số lượng và sổ kho của cửa hàng. Chủ và nhân viên cùng vào được /api/store/**
 * (SecurityConfig); thao tác chỉ dành cho chủ dùng @PreAuthorize. Luôn dùng CurrentUser.storeId(), không tin storeId gửi lên.
 */
@RestController
@RequestMapping("/api/store/batches")
public class StoreBatchController {

    private static final String OWNER_ONLY = "hasRole('STORE_OWNER')";
    private static final BigDecimal MAX_RESCUE_RATIO = new BigDecimal("0.6");

    @GetMapping
    @Operation(tags = StoreApiTags.BATCHES, summary = "Danh sách túi của cửa hàng",
            description = "Chủ và nhân viên đều xem được (nhân viên cần chọn túi để thêm / bớt). status: AVAILABLE, SOLD_OUT, "
                    + "EXPIRED, CANCELLED; bỏ trống = tất cả.")
    @ApiResponse(responseCode = "200", description = "PageResponse<StoreBatchResponse>")
    public PageResponse<StoreBatchResponse> listBatches(
            @Parameter(description = "Lọc theo trạng thái") @RequestParam(required = false) BatchStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.ofList(StoreMockData.batchesOfStore(CurrentUser.storeId(), status), page, size);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(OWNER_ONLY)
    @Operation(tags = StoreApiTags.BATCHES, summary = "Đăng túi mới (multipart, chỉ chủ cửa hàng)",
            description = "Phần `data` là JSON (CreateBatchRequest), phần file `image` tuỳ chọn. Cửa hàng phải APPROVED (BR-02). "
                    + "Ghi sổ kho LISTED (+quantity). DỮ LIỆU GIẢ có kiểm giá và khung giờ để thử đường lỗi.")
    @ApiResponse(responseCode = "201", description = "Túi vừa tạo (AVAILABLE)")
    @ApiResponse(responseCode = "403", description = "STORE_NOT_APPROVED hoặc không phải chủ cửa hàng")
    @ApiResponse(responseCode = "422", description = "PRICE_DISCOUNT_TOO_LOW (giá giải cứu > 60% giá gốc) hoặc PICKUP_TIME_INVALID")
    public StoreBatchResponse createBatch(
            @Valid @RequestPart("data") CreateBatchRequest data,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        validatePrice(data.originalPrice(), data.rescuePrice());
        validatePickup(data.pickupStart(), data.pickupEnd());
        int quantity = data.quantity();
        BigDecimal discount = BigDecimal.ONE.subtract(data.rescuePrice().divide(data.originalPrice(), 4, java.math.RoundingMode.HALF_UP));
        return new StoreBatchResponse(101L, data.categoryId(), "Danh mục " + data.categoryId(), data.title(), data.description(),
                image == null || image.isEmpty() ? null : "/uploads/b101.jpg", data.originalPrice(), data.rescuePrice(),
                discount.multiply(BigDecimal.valueOf(100)).setScale(0, java.math.RoundingMode.HALF_UP).intValue(), quantity,
                quantity, 0, data.pickupStart(), data.pickupEnd(), BatchStatus.AVAILABLE, false, LocalDateTime.now().withNano(0));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(OWNER_ONLY)
    @Operation(tags = StoreApiTags.BATCHES, summary = "Cập nhật túi (multipart, chỉ chủ cửa hàng)",
            description = "Phần `data` là JSON (UpdateBatchRequest), `image` tuỳ chọn (bỏ trống giữ ảnh cũ). Túi đã có đơn thì "
                    + "không đổi được giá và khung giờ (BATCH_HAS_ORDERS). Số lượng đổi qua PATCH /{id}/quantity, không đổi ở đây.")
    @ApiResponse(responseCode = "200", description = "Túi sau khi sửa")
    @ApiResponse(responseCode = "404", description = "NOT_FOUND: túi không thuộc cửa hàng này")
    @ApiResponse(responseCode = "422", description = "BATCH_HAS_ORDERS, PRICE_DISCOUNT_TOO_LOW hoặc PICKUP_TIME_INVALID")
    public StoreBatchResponse updateBatch(
            @PathVariable Long id,
            @Valid @RequestPart("data") UpdateBatchRequest data,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        StoreBatchResponse current = StoreMockData.batch(CurrentUser.storeId(), id);
        validatePrice(data.originalPrice(), data.rescuePrice());
        validatePickup(data.pickupStart(), data.pickupEnd());
        boolean priceOrTimeChanged = current.originalPrice().compareTo(data.originalPrice()) != 0
                || current.rescuePrice().compareTo(data.rescuePrice()) != 0
                || !current.pickupStart().equals(data.pickupStart()) || !current.pickupEnd().equals(data.pickupEnd());
        if (current.hasOrders() && priceOrTimeChanged) {
            throw new BusinessException(ErrorCode.BATCH_HAS_ORDERS);
        }
        return new StoreBatchResponse(current.id(), data.categoryId(), current.categoryName(), data.title(), data.description(),
                image == null || image.isEmpty() ? current.imageUrl() : "/uploads/b" + id + ".jpg", data.originalPrice(),
                data.rescuePrice(), current.discountPercent(), current.quantity(), current.remainingQuantity(),
                current.soldQuantity(), data.pickupStart(), data.pickupEnd(), current.status(), current.hasOrders(),
                current.createdAt());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(OWNER_ONLY)
    @Operation(tags = StoreApiTags.BATCHES, summary = "Huỷ túi (chỉ chủ cửa hàng)",
            description = "BR-24: đơn CONFIRMED thì huỷ và hoàn tiền toàn bộ (BATCH_CANCELLED, đơn sang CANCELLED + REFUND_PENDING); "
                    + "đơn PENDING_PAYMENT thì CANCELLED + UNPAID; cả hai đều trả kho. Ghi nhật ký BATCH_CANCELLED (TC26).")
    @ApiResponse(responseCode = "200", description = "Túi đã CANCELLED kèm số đơn bị ảnh hưởng")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: thiếu lý do")
    @ApiResponse(responseCode = "404", description = "NOT_FOUND")
    public CancelBatchResponse cancelBatch(@PathVariable Long id, @Valid @RequestBody CancelBatchRequest body) {
        StoreBatchResponse current = StoreMockData.batch(CurrentUser.storeId(), id);
        return new CancelBatchResponse(StoreMockData.withStatus(current, BatchStatus.CANCELLED), 1, 1);
    }

    @PatchMapping("/{id}/quantity")
    @Operation(tags = StoreApiTags.STOCK, summary = "Thêm / bớt số lượng túi",
            description = "Chủ và nhân viên đều dùng được. delta > 0 thêm (ADDED), delta < 0 bớt (REMOVED); cộng/trừ cả quantity và "
                    + "remainingQuantity (BR-18), ghi 1 dòng sổ kho (BR-23). Bớt quá số còn lại bị chặn (TC18).")
    @ApiResponse(responseCode = "200", description = "Túi sau khi đổi số lượng (về 0 thì SOLD_OUT)")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: delta bằng 0 hoặc ngoài -999..999")
    @ApiResponse(responseCode = "409", description = "STOCK_NOT_ENOUGH: bớt nhiều hơn số còn lại")
    public StoreBatchResponse adjustQuantity(@PathVariable Long id, @Valid @RequestBody AdjustBatchQuantityRequest body) {
        StoreBatchResponse current = StoreMockData.batch(CurrentUser.storeId(), id);
        int delta = body.delta();
        if (delta == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số lượng thay đổi phải khác 0");
        }
        int remaining = current.remainingQuantity() + delta;
        if (remaining < 0) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "Chỉ còn " + current.remainingQuantity() + " túi, không thể bớt " + (-delta));
        }
        return StoreMockData.withQuantity(current, current.quantity() + delta, remaining);
    }

    @GetMapping("/{id}/stock-movements")
    @Operation(tags = StoreApiTags.STOCK, summary = "Sổ kho của túi",
            description = "Mảng các dòng LISTED, ADDED, REMOVED, ORDERED, RETURNED theo thứ tự thời gian. Sổ kho chỉ thêm, "
                    + "CSDL có trigger chặn sửa / xoá (TC25). Mẫu: kịch bản TC23.")
    @ApiResponse(responseCode = "200", description = "Mảng StockMovementResponse")
    @ApiResponse(responseCode = "404", description = "NOT_FOUND: túi không thuộc cửa hàng này")
    public List<StockMovementResponse> listStockMovements(@PathVariable Long id) {
        StoreMockData.batch(CurrentUser.storeId(), id);
        return StoreMockData.stockMovements();
    }

    private static void validatePrice(BigDecimal original, BigDecimal rescue) {
        if (rescue.compareTo(original.multiply(MAX_RESCUE_RATIO)) > 0) {
            throw new BusinessException(ErrorCode.PRICE_DISCOUNT_TOO_LOW);
        }
    }

    private static void validatePickup(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start) || !end.isAfter(LocalDateTime.now().withNano(0))) {
            throw new BusinessException(ErrorCode.PICKUP_TIME_INVALID);
        }
    }
}
