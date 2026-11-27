package com.foodrescue.order.domain.exception;

public class OrderException extends RuntimeException {
    private final String code;
    private final int status;

    public OrderException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }

    public static OrderException notFound(String message) {
        return new OrderException("NOT_FOUND", message, 404);
    }

    public static OrderException forbidden(String message) {
        return new OrderException("FORBIDDEN", message, 403);
    }

    public static OrderException cartEmpty() {
        return new OrderException("CART_EMPTY", "Giỏ hàng đang trống", 422);
    }

    public static OrderException multiStoreCheckout() {
        return new OrderException("MULTI_STORE_CHECKOUT", "Giỏ hàng chỉ được chứa sản phẩm của 1 cửa hàng", 422);
    }

    public static OrderException conflict(String code, String message) {
        return new OrderException(code, message, 409);
    }

    public static OrderException validation(String message) {
        return new OrderException("VALIDATION_ERROR", message, 400);
    }

    public static OrderException serviceUnavailable(String message) {
        return new OrderException("DEPENDENCY_UNAVAILABLE", message, 503);
    }
}
