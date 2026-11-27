package com.foodrescue.finance.api;

import com.foodrescue.finance.domain.exception.FinanceException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class FinanceErrors {
    @ExceptionHandler(FinanceException.class)
    ResponseEntity<?> business(FinanceException ex, HttpServletRequest request) {
        return error(ex.status(), ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.web.bind.MissingRequestHeaderException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<?> validation(Exception ex, HttpServletRequest request) {
        return error(400, "VALIDATION_ERROR", "Dữ liệu yêu cầu không hợp lệ", request);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(Exception ex, HttpServletRequest request) {
        return error(
                409, "IDEMPOTENCY_CONFLICT", "Dữ liệu xung đột với giao dịch hiện tại", request);
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<?> forbidden(
            org.springframework.web.server.ResponseStatusException ex, HttpServletRequest request) {
        return error(ex.getStatusCode().value(), "FORBIDDEN", "Không có quyền truy cập", request);
    }

    private ResponseEntity<?> error(
            int status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(
                        Map.of(
                                "code",
                                code,
                                "message",
                                message,
                                "details",
                                java.util.List.of(),
                                "timestamp",
                                Instant.now(),
                                "path",
                                request.getRequestURI()));
    }
}
