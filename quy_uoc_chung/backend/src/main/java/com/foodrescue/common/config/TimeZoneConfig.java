package com.foodrescue.common.config;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.context.annotation.Configuration;

/** Cả ứng dụng chạy theo giờ Việt Nam (BR-26), khớp SET time_zone = '+07:00' của CSDL. */
@Configuration
public class TimeZoneConfig {

    public static final String ZONE = "Asia/Ho_Chi_Minh";

    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
    }
}
