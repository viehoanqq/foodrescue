package com.foodrescue.common.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Định dạng lỗi DUY NHẤT của mọi API.
 * <pre>
 * { "code": "BATCH_SOLD_OUT", "message": "Túi vừa được giải cứu hết",
 *   "details": [], "timestamp": "2026-09-25T21:05:00", "path": "/api/orders" }
 * </pre>
 * Frontend hiển thị {@code message}; dùng {@code code} khi cần xử lý riêng (vd CART_OTHER_STORE).
 */
public record ApiError(
        String code,
        String message,
        List<FieldError> details,
        LocalDateTime timestamp,
        String path) {

    /** Lỗi của từng trường khi validate form: { "field": "rescuePrice", "message": "..." } */
    public record FieldError(String field, String message) {
    }
}
