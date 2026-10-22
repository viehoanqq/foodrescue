package com.foodrescue.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Phần "data" (JSON) của POST /api/auth/register-store (multipart). Phần file: "license" (bắt buộc), "image" (tuỳ chọn).
 * Tạo 1 user STORE_OWNER + 1 cửa hàng PENDING trong cùng 1 transaction (stores.owner_id NOT NULL).
 * Ràng buộc nghiệp vụ kiểm ở service: closeTime phải sau openTime (chk_store_time).
 */
public record RegisterStoreRequest(
        @Schema(description = "Họ tên chủ cửa hàng", example = "Võ Thị Hạnh")
        @NotBlank(message = "Nhập họ tên") @Size(max = 100) String fullName,
        @Schema(description = "Email đăng nhập (duy nhất)", example = "owner.moi@gmail.com")
        @NotBlank(message = "Nhập email") @Email(message = "Email không hợp lệ") @Size(max = 100) String email,
        @Schema(description = "Mật khẩu, tối thiểu 6 ký tự", example = "123456")
        @NotBlank(message = "Nhập mật khẩu") @Size(min = 6, max = 72, message = "Mật khẩu tối thiểu 6 ký tự") String password,
        @Schema(description = "Số điện thoại chủ cửa hàng", example = "0912345674")
        @NotBlank(message = "Nhập số điện thoại") @Size(max = 20) String phone,
        @Schema(description = "Tên cửa hàng", example = "GreenMart Rau Củ Sạch")
        @NotBlank(message = "Nhập tên cửa hàng") @Size(max = 150) String storeName,
        @Schema(description = "Giới thiệu ngắn", example = "Rau củ quả hữu cơ.")
        @Size(max = 2000) String description,
        @Schema(description = "Số điện thoại cửa hàng", example = "02838300004")
        @NotBlank(message = "Nhập số điện thoại cửa hàng") @Size(max = 20) String storePhone,
        @Schema(description = "Địa chỉ", example = "12 Nguyễn Thị Minh Khai, Đa Kao, Quận 1, TP.HCM")
        @NotBlank(message = "Nhập địa chỉ") @Size(max = 255) String address,
        @Schema(description = "Vĩ độ ghim trên bản đồ (trong lãnh thổ Việt Nam: 8.0 đến 23.5)", example = "10.78756")
        @NotNull(message = "Chọn vị trí trên bản đồ")
        @DecimalMin(value = "8.0", message = "Vị trí phải thuộc lãnh thổ Việt Nam")
        @DecimalMax(value = "23.5", message = "Vị trí phải thuộc lãnh thổ Việt Nam") BigDecimal latitude,
        @Schema(description = "Kinh độ ghim trên bản đồ (102.0 đến 110.0)", example = "106.6998")
        @NotNull(message = "Chọn vị trí trên bản đồ")
        @DecimalMin(value = "102.0", message = "Vị trí phải thuộc lãnh thổ Việt Nam")
        @DecimalMax(value = "110.0", message = "Vị trí phải thuộc lãnh thổ Việt Nam") BigDecimal longitude,
        @Schema(description = "Giờ mở cửa (HH:mm:ss)", example = "07:00:00")
        @NotNull(message = "Chọn giờ mở cửa") LocalTime openTime,
        @Schema(description = "Giờ đóng cửa (HH:mm:ss), phải sau giờ mở cửa", example = "21:00:00")
        @NotNull(message = "Chọn giờ đóng cửa") LocalTime closeTime,
        @Schema(description = "Ngân hàng nhận tiền chi trả", example = "MB Bank")
        @NotBlank(message = "Nhập tên ngân hàng") @Size(max = 100) String bankName,
        @Schema(description = "Số tài khoản", example = "0123456789")
        @NotBlank(message = "Nhập số tài khoản") @Size(max = 30) String bankAccountNo,
        @Schema(description = "Tên chủ tài khoản", example = "VO THI HANH")
        @NotBlank(message = "Nhập tên chủ tài khoản") @Size(max = 100) String bankAccountName) {
}
