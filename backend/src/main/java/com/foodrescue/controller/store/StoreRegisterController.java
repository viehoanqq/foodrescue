package com.foodrescue.controller.store;

import com.foodrescue.controller.mock.StoreMockData;
import com.foodrescue.dto.request.RegisterStoreRequest;
import com.foodrescue.dto.response.StoreResponse;
import com.foodrescue.entity.enums.StoreStatus;
import com.foodrescue.exception.BusinessException;
import com.foodrescue.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** H2 (dữ liệu giả): đăng ký chủ cửa hàng + cửa hàng. Công khai (đã nằm trong /api/auth/** của SecurityConfig). */
@RestController
@RequestMapping("/api/auth/register-store")
public class StoreRegisterController {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(tags = StoreApiTags.REGISTER, summary = "Đăng ký chủ cửa hàng và cửa hàng (multipart)",
            description = "Phần `data` là JSON (RegisterStoreRequest); phần file: `license` (ảnh giấy phép VSATTP, bắt buộc) "
                    + "và `image` (ảnh cửa hàng, tuỳ chọn). Tạo user STORE_OWNER + cửa hàng PENDING (BR-02), "
                    + "tỉ lệ phí mặc định 15% cho tới khi admin duyệt. DỮ LIỆU GIẢ: email đã có trong dữ liệu mẫu trả EMAIL_EXISTS.")
    @ApiResponse(responseCode = "201", description = "Tạo thành công, cửa hàng ở trạng thái PENDING")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: thiếu hoặc sai dữ liệu, chưa tải ảnh giấy phép")
    @ApiResponse(responseCode = "409", description = "EMAIL_EXISTS: email đã được sử dụng")
    public StoreResponse registerStore(
            @Valid @RequestPart("data") RegisterStoreRequest data,
            @RequestPart("license") MultipartFile license,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        if (license == null || license.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cần tải ảnh giấy phép vệ sinh an toàn thực phẩm");
        }
        if (!data.closeTime().isAfter(data.openTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giờ đóng cửa phải sau giờ mở cửa");
        }
        if (StoreMockData.emailExists(data.email())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }
        return new StoreResponse(5L, 12L, data.fullName(), data.email(), data.storeName(), data.description(),
                data.storePhone(), data.address(), data.latitude(), data.longitude(), null, "/uploads/license-5.jpg",
                data.openTime(), data.closeTime(), StoreStatus.PENDING, null, new java.math.BigDecimal("15.00"),
                data.bankName(), data.bankAccountNo(), data.bankAccountName(), LocalDateTime.now().withNano(0));
    }
}
