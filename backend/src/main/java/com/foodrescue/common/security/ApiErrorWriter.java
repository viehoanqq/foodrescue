package com.foodrescue.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.common.api.ApiError;
import com.foodrescue.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Ghi lỗi dạng ApiError cho những chỗ nằm NGOÀI controller (filter, Spring Security),
 * nơi GlobalExceptionHandler không bắt được.
 */
@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest req, HttpServletResponse res, ErrorCode code) throws IOException {
        res.setStatus(code.status());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        ApiError body = new ApiError(code.name(), code.defaultMessage(), List.of(), LocalDateTime.now(),
                req.getRequestURI());
        objectMapper.writeValue(res.getOutputStream(), body);
    }
}
