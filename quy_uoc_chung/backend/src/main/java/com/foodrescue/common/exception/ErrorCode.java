package com.foodrescue.common.exception;

/**
 * Danh sách mã lỗi dùng chung. Thêm mã mới: thêm vào CUỐI nhóm tương ứng, qua PR của TV1.
 * Frontend chỉ hiển thị message; code dùng để xử lý riêng khi cần.
 */
public enum ErrorCode {
    // ---- Chung ----
    VALIDATION_ERROR(400, "Dữ liệu không hợp lệ"),
    UNAUTHORIZED(401, "Vui lòng đăng nhập"),
    FORBIDDEN(403, "Bạn không có quyền thực hiện thao tác này"),
    NOT_FOUND(404, "Không tìm thấy dữ liệu"),
    DATA_CONSTRAINT_VIOLATION(422, "Dữ liệu vi phạm quy tắc của hệ thống"),
    EXTERNAL_SERVICE_ERROR(502, "Dịch vụ bên ngoài đang lỗi, vui lòng thử lại"),
    INTERNAL_ERROR(500, "Có lỗi xảy ra, vui lòng thử lại"),

    // ---- Tài khoản (TV1) ----
    INVALID_CREDENTIALS(401, "Email hoặc mật khẩu không đúng"),
    ACCOUNT_LOCKED(403, "Tài khoản đã bị khoá"),
    PASSWORD_CHANGE_REQUIRED(403, "Vui lòng đổi mật khẩu trước khi tiếp tục"),
    EMAIL_EXISTS(409, "Email đã được sử dụng"),
    WRONG_OLD_PASSWORD(422, "Mật khẩu hiện tại không đúng"),

    // ---- Cửa hàng, túi, kho (TV2) ----
    STORE_NOT_APPROVED(403, "Cửa hàng chưa được duyệt"),
    STORE_ALREADY_EXISTS(409, "Tài khoản này đã có cửa hàng"),
    PRICE_DISCOUNT_TOO_LOW(422, "Giá giải cứu phải giảm ít nhất 40% so với giá gốc"),
    PICKUP_TIME_INVALID(422, "Khung giờ lấy hàng không hợp lệ"),
    BATCH_HAS_ORDERS(422, "Túi đã có đơn, không sửa được giá hoặc khung giờ"),
    STOCK_NOT_ENOUGH(409, "Số lượng còn lại không đủ"),

    // ---- Tìm kiếm, đánh giá (TV3) ----
    REVIEW_NOT_ALLOWED(422, "Chỉ đánh giá được đơn đã nhận hàng, mỗi đơn 1 lần"),

    // ---- Giỏ hàng, đơn, thanh toán, giao hàng (TV4) ----
    BATCH_NOT_AVAILABLE(409, "Túi này hiện không còn bán"),
    BATCH_SOLD_OUT(409, "Túi vừa được giải cứu hết"),
    CART_EMPTY(422, "Giỏ hàng đang trống"),
    CART_OTHER_STORE(409, "Giỏ hàng đang có túi của cửa hàng khác"),
    CART_ITEM_LIMIT(422, "Mỗi loại túi chỉ được mua từ 1 đến 5 cái"),
    PICKUP_WINDOW_CONFLICT(422, "Các túi trong giỏ không có khung giờ lấy chung"),
    ORDER_NOT_CANCELLABLE(422, "Đơn hàng không thể huỷ ở trạng thái hiện tại"),
    PICKUP_CODE_INVALID(404, "Mã nhận hàng không đúng hoặc không thuộc cửa hàng"),
    PICKUP_TOO_EARLY(422, "Chưa tới giờ nhận hàng"),
    PICKUP_TOO_LATE(422, "Đã quá giờ nhận hàng"),
    PAYMENT_INVALID_SIGNATURE(400, "Chữ ký thanh toán không hợp lệ"),
    PAYMENT_AMOUNT_MISMATCH(400, "Số tiền thanh toán không khớp"),

    // ---- Tiền: hoàn tiền, chi trả (TV1) ----
    REFUND_REF_REQUIRED(422, "Cần nhập mã giao dịch hoàn tiền"),
    PAYOUT_NO_ORDERS(422, "Không có đơn nào đủ điều kiện chi trả"),
    PAYOUT_PENDING_EXISTS(409, "Cửa hàng đang có kỳ chi trả chờ xử lý"),
    PAYOUT_REF_REQUIRED(422, "Cần nhập mã chuyển khoản"),
    PAYOUT_NOT_CANCELLABLE(422, "Kỳ chi trả đã chi, không huỷ được");

    private final int status;
    private final String defaultMessage;

    ErrorCode(int status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public int status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
