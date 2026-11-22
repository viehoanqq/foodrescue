package com.foodrescue.buildingblocks.health;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Imported explicitly by each service; never returns credentials or SQL errors. */
@RestController
public class DatabaseHealthController {
    private final DataSource dataSource;
    private final String service;

    public DatabaseHealthController(DataSource dataSource,
                                    @Value("${spring.application.name}") String service) {
        this.dataSource = dataSource;
        this.service = service;
    }

    @GetMapping("/internal/health/db")
    public ResponseEntity<DatabaseHealth> check() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return ResponseEntity.ok(new DatabaseHealth(service, "UP"));
            }
        } catch (Exception ignored) {
            // Health response must not disclose connection details.
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new DatabaseHealth(service, "DOWN"));
    }

    public record DatabaseHealth(String service, String database) {}
}
