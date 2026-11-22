package com.foodrescue.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.identity.domain.exception.IdentityException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdentityError {
    private final ObjectMapper mapper;

    public IdentityError(ObjectMapper mapper) { this.mapper = mapper; }

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<Body> handle(IdentityException ex, HttpServletRequest req) {
        return ResponseEntity.status(ex.status()).body(body(ex.code(), ex.getMessage(), List.of(), req));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Body> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldError(f.getField(), f.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().body(body("VALIDATION_ERROR", "Dữ liệu không hợp lệ", details, req));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Body> constraint(DataIntegrityViolationException ex, HttpServletRequest req) {
        return ResponseEntity.status(409).body(body("DATA_CONSTRAINT_VIOLATION", "Dữ liệu bị trùng hoặc không hợp lệ", List.of(), req));
    }

    public void write(HttpServletRequest req, HttpServletResponse res, int status, String code, String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), body(code, message, List.of(), req));
    }

    private Body body(String code, String message, List<FieldError> details, HttpServletRequest req) {
        return new Body(code, message, details, OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")),
                req.getRequestURI());
    }

    public record FieldError(String field, String message) {}
    public record Body(String code, String message, List<FieldError> details,
                       OffsetDateTime timestamp, String path) {}
}
