package com.foodrescue.catalog.api;

import com.foodrescue.catalog.domain.exception.CatalogException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class CatalogError {
    @ExceptionHandler(CatalogException.class)
    public ResponseEntity<ErrorBody> catalog(CatalogException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).body(body(ex.code(), ex.getMessage(), List.of(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(field -> new FieldError(field.getField(), field.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(body("VALIDATION_ERROR", "Dữ liệu không hợp lệ", details, request));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorBody> badInput(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(body("VALIDATION_ERROR", "Yêu cầu không hợp lệ", List.of(), request));
    }

    private ErrorBody body(String code, String message, List<FieldError> details, HttpServletRequest request) {
        return new ErrorBody(code, message, details, OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")),
                request.getRequestURI());
    }

    public record FieldError(String field, String message) {}
    public record ErrorBody(String code, String message, List<FieldError> details,
                            OffsetDateTime timestamp, String path) {}
}
