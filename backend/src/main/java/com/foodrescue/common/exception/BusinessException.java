package com.foodrescue.common.exception;

/**
 * Lỗi nghiệp vụ. Service ném ra, GlobalExceptionHandler chuyển thành ApiError.
 * <pre>
 * throw new BusinessException(ErrorCode.BATCH_SOLD_OUT);
 * throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "Túi \"Bánh Kem Lát\" chỉ còn 1 cái");
 * </pre>
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
