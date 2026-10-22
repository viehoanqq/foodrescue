package com.foodrescue.controller.store;

import com.foodrescue.controller.mock.StoreMockData;
import com.foodrescue.dto.request.UpdateStoreProfileRequest;
import com.foodrescue.dto.response.StoreResponse;
import com.foodrescue.exception.BusinessException;
import com.foodrescue.exception.ErrorCode;
import com.foodrescue.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** H2 (dữ liệu giả): hồ sơ cửa hàng của người đang đăng nhập (chủ hoặc nhân viên). */
@RestController
@RequestMapping("/api/store/profile")
public class StoreProfileController {

    @GetMapping
    @Operation(tags = StoreApiTags.PROFILE, summary = "Xem hồ sơ cửa hàng",
            description = "Chủ và nhân viên đều xem được; cửa hàng lấy từ token (CurrentUser.storeId()).")
    @ApiResponse(responseCode = "200", description = "StoreResponse")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: tài khoản không thuộc cửa hàng nào")
    public StoreResponse getProfile() {
        return StoreMockData.store(CurrentUser.storeId());
    }

    @PutMapping
    @PreAuthorize("hasRole('STORE_OWNER')")
    @Operation(tags = StoreApiTags.PROFILE, summary = "Cập nhật hồ sơ cửa hàng (chỉ chủ cửa hàng)",
            description = "Đổi tên, giới thiệu, liên hệ, địa chỉ, vị trí (trong lãnh thổ Việt Nam), giờ mở cửa và tài khoản ngân hàng. "
                    + "Không đổi được status và commissionRate (của admin). Đổi vị trí thì khoảng cách ở trang chủ đổi theo.")
    @ApiResponse(responseCode = "200", description = "Hồ sơ sau khi sửa")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: vị trí ngoài Việt Nam, closeTime không sau openTime...")
    public StoreResponse updateProfile(@Valid @RequestBody UpdateStoreProfileRequest body) {
        if (!body.closeTime().isAfter(body.openTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giờ đóng cửa phải sau giờ mở cửa");
        }
        StoreResponse s = StoreMockData.store(CurrentUser.storeId());
        return new StoreResponse(s.id(), s.ownerId(), s.ownerName(), s.ownerEmail(), body.name(), body.description(),
                body.phone(), body.address(), body.latitude(), body.longitude(), s.imageUrl(), s.licenseImageUrl(),
                body.openTime(), body.closeTime(), s.status(), s.rejectReason(), s.commissionRate(), body.bankName(),
                body.bankAccountNo(), body.bankAccountName(), s.createdAt());
    }
}
