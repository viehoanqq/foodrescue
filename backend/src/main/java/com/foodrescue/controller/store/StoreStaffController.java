package com.foodrescue.controller.store;

import com.foodrescue.controller.mock.StoreMockData;
import com.foodrescue.dto.request.CreateStaffRequest;
import com.foodrescue.dto.response.StaffResponse;
import com.foodrescue.entity.enums.UserStatus;
import com.foodrescue.exception.BusinessException;
import com.foodrescue.exception.ErrorCode;
import com.foodrescue.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** H2 (dữ liệu giả): chủ cửa hàng quản lý tài khoản nhân viên quầy. Chỉ STORE_OWNER. */
@RestController
@RequestMapping("/api/store/staff")
@PreAuthorize("hasRole('STORE_OWNER')")
public class StoreStaffController {

    @GetMapping
    @Operation(tags = StoreApiTags.STAFF, summary = "Danh sách nhân viên của cửa hàng")
    @ApiResponse(responseCode = "200", description = "Mảng StaffResponse")
    @ApiResponse(responseCode = "403", description = "FORBIDDEN: không phải chủ cửa hàng")
    public List<StaffResponse> listStaff() {
        return StoreMockData.staffOfStore(CurrentUser.storeId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(tags = StoreApiTags.STAFF, summary = "Tạo tài khoản nhân viên",
            description = "Tạo STORE_STAFF thuộc cửa hàng của chủ với mật khẩu tạm; must_change_password = TRUE nên nhân viên "
                    + "đăng nhập lần đầu phải đổi mật khẩu (BR-01).")
    @ApiResponse(responseCode = "201", description = "Nhân viên vừa tạo")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR")
    @ApiResponse(responseCode = "409", description = "EMAIL_EXISTS")
    public StaffResponse createStaff(@Valid @RequestBody CreateStaffRequest body) {
        if (StoreMockData.emailExists(body.email())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTS);
        }
        return new StaffResponse(12L, body.email(), body.fullName(), body.phone(), UserStatus.ACTIVE, true, LocalDateTime.now().withNano(0));
    }

    @PostMapping("/{id}/lock")
    @Operation(tags = StoreApiTags.STAFF, summary = "Khoá nhân viên",
            description = "Chỉ khoá được nhân viên thuộc cửa hàng của mình; có hiệu lực ngay.")
    @ApiResponse(responseCode = "200", description = "Nhân viên sau khi khoá (LOCKED)")
    @ApiResponse(responseCode = "404", description = "NOT_FOUND: không phải nhân viên của cửa hàng này")
    public StaffResponse lockStaff(@PathVariable Long id) {
        return StoreMockData.withStatus(StoreMockData.staff(CurrentUser.storeId(), id), UserStatus.LOCKED);
    }

    @PostMapping("/{id}/unlock")
    @Operation(tags = StoreApiTags.STAFF, summary = "Mở khoá nhân viên")
    @ApiResponse(responseCode = "200", description = "Nhân viên sau khi mở khoá (ACTIVE)")
    @ApiResponse(responseCode = "404", description = "NOT_FOUND: không phải nhân viên của cửa hàng này")
    public StaffResponse unlockStaff(@PathVariable Long id) {
        return StoreMockData.withStatus(StoreMockData.staff(CurrentUser.storeId(), id), UserStatus.ACTIVE);
    }
}
