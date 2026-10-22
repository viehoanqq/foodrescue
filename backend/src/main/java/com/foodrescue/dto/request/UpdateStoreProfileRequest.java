package com.foodrescue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Body của PUT /api/store/profile (chỉ STORE_OWNER). Đổi vị trí thì khoảng cách trên trang chủ đổi theo.
 * Tài khoản ngân hàng chỉ ảnh hưởng các kỳ chi trả tạo SAU (kỳ cũ đã chụp lại thông tin).
 */
public record UpdateStoreProfileRequest(
        @Schema(example = "Sweet Paris Bakery & Coffee") @NotBlank(message = "Nhập tên cửa hàng") @Size(max = 150) String name,
        @Schema(example = "Bánh ngọt kiểu Pháp và cà phê.") @Size(max = 2000) String description,
        @Schema(example = "02838300002") @NotBlank(message = "Nhập số điện thoại") @Size(max = 20) String phone,
        @Schema(example = "45 Lê Duẩn, Bến Nghé, Quận 1, TP.HCM") @NotBlank(message = "Nhập địa chỉ") @Size(max = 255) String address,
        @Schema(description = "Vĩ độ (8.0 đến 23.5)", example = "10.781254")
        @NotNull(message = "Chọn vị trí trên bản đồ")
        @DecimalMin(value = "8.0", message = "Vị trí phải thuộc lãnh thổ Việt Nam")
        @DecimalMax(value = "23.5", message = "Vị trí phải thuộc lãnh thổ Việt Nam") BigDecimal latitude,
        @Schema(description = "Kinh độ (102.0 đến 110.0)", example = "106.698942")
        @NotNull(message = "Chọn vị trí trên bản đồ")
        @DecimalMin(value = "102.0", message = "Vị trí phải thuộc lãnh thổ Việt Nam")
        @DecimalMax(value = "110.0", message = "Vị trí phải thuộc lãnh thổ Việt Nam") BigDecimal longitude,
        @Schema(example = "07:00:00") @NotNull(message = "Chọn giờ mở cửa") LocalTime openTime,
        @Schema(example = "22:00:00") @NotNull(message = "Chọn giờ đóng cửa") LocalTime closeTime,
        @Schema(example = "Techcombank") @NotBlank(message = "Nhập tên ngân hàng") @Size(max = 100) String bankName,
        @Schema(example = "19031234567890") @NotBlank(message = "Nhập số tài khoản") @Size(max = 30) String bankAccountNo,
        @Schema(example = "LE THI THU THAO") @NotBlank(message = "Nhập tên chủ tài khoản") @Size(max = 100) String bankAccountName) {
}
