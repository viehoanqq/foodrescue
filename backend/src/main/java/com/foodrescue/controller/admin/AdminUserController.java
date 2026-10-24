package com.foodrescue.controller.admin;

import com.foodrescue.controller.mock.StoreMockData;
import com.foodrescue.controller.store.StoreApiTags;
import com.foodrescue.dto.response.PageResponse;
import com.foodrescue.dto.response.ResetPasswordResponse;
import com.foodrescue.dto.response.UserSummaryResponse;
import com.foodrescue.entity.enums.Role;
import com.foodrescue.entity.enums.UserStatus;
import com.foodrescue.exception.BusinessException;
import com.foodrescue.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** H2 (dữ liệu giả): admin quản lý người dùng. Quyền ADMIN do SecurityConfig (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @GetMapping
    @Operation(tags = StoreApiTags.ADMIN_USERS, summary = "Danh sách người dùng",
            description = "Lọc theo role, status và từ khoá (khớp email, họ tên, số điện thoại). Không trả mật khẩu.")
    @ApiResponse(responseCode = "200", description = "PageResponse<UserSummaryResponse>")
    public PageResponse<UserSummaryResponse> listUsers(
            @Parameter(description = "ADMIN, STORE_OWNER, STORE_STAFF, CUSTOMER") @RequestParam(required = false) Role role,
            @Parameter(description = "ACTIVE hoặc LOCKED") @RequestParam(required = false) UserStatus status,
            @Parameter(description = "Từ khoá tìm kiếm") @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String key = keyword == null ? "" : keyword.trim().toLowerCase();
        List<UserSummaryResponse> rows = StoreMockData.USERS.stream()
                .filter(u -> role == null || u.role() == role)
                .filter(u -> status == null || u.status() == status)
                .filter(u -> key.isEmpty() || u.email().toLowerCase().contains(key)
                        || u.fullName().toLowerCase().contains(key) || u.phone().contains(key))
                .toList();
        return PageResponse.ofList(rows, page, size);
    }

    @PostMapping("/{id}/lock")
    @Operation(tags = StoreApiTags.ADMIN_USERS, summary = "Khoá tài khoản",
            description = "Tài khoản bị khoá không đăng nhập được và có hiệu lực ngay (JwtAuthFilter đọc lại CSDL mỗi request). "
                    + "Ghi nhật ký USER_LOCKED. Admin không tự khoá chính mình.")
    @ApiResponse(responseCode = "200", description = "Người dùng sau khi khoá (LOCKED)")
    @ApiResponse(responseCode = "404", description = "NOT_FOUND")
    public UserSummaryResponse lockUser(@PathVariable Long id) {
        UserSummaryResponse user = StoreMockData.user(id);
        if (user.role() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không thể khoá tài khoản quản trị viên");
        }
        return StoreMockData.withStatus(user, UserStatus.LOCKED, user.mustChangePassword());
    }

    @PostMapping("/{id}/unlock")
    @Operation(tags = StoreApiTags.ADMIN_USERS, summary = "Mở khoá tài khoản", description = "Ghi nhật ký USER_UNLOCKED.")
    @ApiResponse(responseCode = "200", description = "Người dùng sau khi mở khoá (ACTIVE)")
    @ApiResponse(responseCode = "404", description = "NOT_FOUND")
    public UserSummaryResponse unlockUser(@PathVariable Long id) {
        UserSummaryResponse user = StoreMockData.user(id);
        return StoreMockData.withStatus(user, UserStatus.ACTIVE, user.mustChangePassword());
    }

    @PostMapping("/{id}/reset-password")
    @Operation(tags = StoreApiTags.ADMIN_USERS, summary = "Đặt lại mật khẩu tạm",
            description = "Sinh mật khẩu tạm, đặt must_change_password = TRUE: người dùng đăng nhập xong chỉ gọi được "
                    + "GET /api/me và PUT /api/me/password (PASSWORD_CHANGE_REQUIRED, TC24). Mật khẩu tạm chỉ trả đúng 1 lần. "
                    + "Ghi nhật ký USER_PASSWORD_RESET.")
    @ApiResponse(responseCode = "200", description = "Mật khẩu tạm")
    @ApiResponse(responseCode = "404", description = "NOT_FOUND")
    public ResetPasswordResponse resetPassword(@PathVariable Long id) {
        UserSummaryResponse user = StoreMockData.user(id);
        return new ResetPasswordResponse(user.id(), "Tmp8x2kQ", true);
    }
}
