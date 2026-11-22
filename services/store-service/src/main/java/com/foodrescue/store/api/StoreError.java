package com.foodrescue.store.api;

import com.foodrescue.store.domain.exception.StoreException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class StoreError {
    @ExceptionHandler(StoreException.class)
    public ResponseEntity<ErrorBody> store(StoreException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).body(body(ex.code(), ex.getMessage(), List.of(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(field -> new FieldError(field.getField(), field.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(body("VALIDATION_ERROR", "Dữ liệu không hợp lệ", details, request));
    }

    private ErrorBody body(String code, String message, List<FieldError> details, HttpServletRequest request) {
        return new ErrorBody(code, message, details, OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")),
                request.getRequestURI());
    }

    public record FieldError(String field, String message) {}
    public record ErrorBody(String code, String message, List<FieldError> details,
                            OffsetDateTime timestamp, String path) {}
}
