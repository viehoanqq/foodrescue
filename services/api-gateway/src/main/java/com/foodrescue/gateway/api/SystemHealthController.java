package com.foodrescue.gateway.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Calls the five services' DB probes concurrently; it does not connect to their databases. */
@RestController
public class SystemHealthController {
    private final WebClient client;
    private final Map<String, String> targets;

    public SystemHealthController(WebClient.Builder client,
            @Value("${app.health.identity-url}") String identityUrl,
            @Value("${app.health.store-url}") String storeUrl,
            @Value("${app.health.catalog-url}") String catalogUrl,
            @Value("${app.health.order-url}") String orderUrl,
            @Value("${app.health.finance-url}") String financeUrl) {
        this.client = client.build();
        targets = new LinkedHashMap<>();
        targets.put("identity-service", identityUrl);
        targets.put("store-service", storeUrl);
        targets.put("catalog-inventory-service", catalogUrl);
        targets.put("order-service", orderUrl);
        targets.put("payment-finance-service", financeUrl);
    }

    @GetMapping("/api/health")
    public Mono<ResponseEntity<SystemHealth>> check() {
        List<String> names = new ArrayList<>(targets.keySet());
        List<Mono<String>> probes = names.stream().map(name -> probe(targets.get(name))).toList();
        return Mono.zip(probes, statuses -> {
            Map<String, ServiceHealth> services = new LinkedHashMap<>();
            for (int i = 0; i < names.size(); i++) {
                services.put(names.get(i), new ServiceHealth((String) statuses[i]));
            }
            boolean healthy = services.values().stream().allMatch(s -> "UP".equals(s.database()));
            return ResponseEntity.status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new SystemHealth(healthy ? "UP" : "DOWN", services));
        });
    }

    private Mono<String> probe(String baseUrl) {
        return client.get().uri(baseUrl + "/internal/health/db")
                .exchangeToMono(response -> Mono.just(response.statusCode().is2xxSuccessful() ? "UP" : "DOWN"))
                .timeout(Duration.ofSeconds(3))
                .onErrorReturn("DOWN");
    }

    public record ServiceHealth(String database) {}
    public record SystemHealth(String status, Map<String, ServiceHealth> services) {}
}
