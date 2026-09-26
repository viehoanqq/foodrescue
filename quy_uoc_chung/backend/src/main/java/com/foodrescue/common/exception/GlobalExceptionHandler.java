package com.foodrescue.common.exception;

import com.foodrescue.common.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Mọi lỗi của API đều đi qua đây và trả về cùng một định dạng ApiError. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
        return build(ex.getCode(), ex.getMessage(), List.of(), req);
    }

    /** Lỗi @Valid trên request body: trả từng trường cho form hiển thị dưới ô nhập. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), details, req);
    }

    /** Lỗi @Validated trên tham số (query, path). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<ApiError.FieldError> details = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), details, req);
    }

    /** Chốt chặn cuối: CHECK / UNIQUE / FK trong MySQL từ chối dữ liệu. Code đúng thì không bao giờ tới đây. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Vi phạm ràng buộc CSDL tại {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.DATA_CONSTRAINT_VIOLATION, ErrorCode.DATA_CONSTRAINT_VIOLATION.defaultMessage(),
                List.of(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest req) {
        log.error("Lỗi không mong muốn tại {}", req.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), List.of(), req);
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message, List<ApiError.FieldError> details,
                                           HttpServletRequest req) {
        ApiError body = new ApiError(code.name(), message, details, LocalDateTime.now(), req.getRequestURI());
        return ResponseEntity.status(code.status()).body(body);
    }
}
