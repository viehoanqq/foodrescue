package com.foodrescue.store.infrastructure.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StoreUploadConfiguration implements WebMvcConfigurer {
    @Value("${app.upload-dir:uploads}") private String uploadDir;
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/images/**")
                .addResourceLocations(Path.of(uploadDir).toAbsolutePath().normalize().resolve("images").toUri().toString());
    }
}
