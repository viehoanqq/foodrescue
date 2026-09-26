package com.foodrescue.common.api;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Kiểm tra backend đang chạy: GET /api/ping (công khai). */
@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of("status", "ok", "time", LocalDateTime.now());
    }
}
