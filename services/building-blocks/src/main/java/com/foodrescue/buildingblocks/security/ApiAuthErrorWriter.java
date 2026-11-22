package com.foodrescue.buildingblocks.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.MediaType;

public final class ApiAuthErrorWriter {
    private final ObjectMapper mapper;

    public ApiAuthErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(),
                new Body(code, message, List.of(), OffsetDateTime.now(ZoneOffset.UTC), request.getRequestURI()));
    }

    public record Body(String code, String message, List<Object> details,
                       OffsetDateTime timestamp, String path) {}
}
