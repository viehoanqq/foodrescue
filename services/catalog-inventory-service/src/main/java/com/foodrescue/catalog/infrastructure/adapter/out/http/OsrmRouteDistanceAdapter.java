package com.foodrescue.catalog.infrastructure.adapter.out.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodrescue.catalog.application.port.out.IRouteDistancePort;
import com.foodrescue.catalog.domain.model.CatalogStoreSummary;
import com.foodrescue.catalog.domain.model.RouteMetrics;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OsrmRouteDistanceAdapter implements IRouteDistancePort {
    private static final Logger log = LoggerFactory.getLogger(OsrmRouteDistanceAdapter.class);
    private final String baseUrl;
    private final RestClient client;

    @Autowired
    public OsrmRouteDistanceAdapter(@Value("${app.osrm.base-url:}") String configuredUrl) {
        this(configuredUrl, RestClient.builder().requestFactory(
                configuredUrl == null || configuredUrl.isBlank()
                        ? new org.springframework.http.client.SimpleClientHttpRequestFactory()
                        : defaultRequestFactory()));
    }

    OsrmRouteDistanceAdapter(String configuredUrl, RestClient.Builder builder) {
        this.baseUrl = configuredUrl == null ? "" : configuredUrl.trim().replaceAll("/+$", "");
        if (!this.baseUrl.isEmpty()) {
            URI uri = URI.create(this.baseUrl);
            if (uri.getScheme() == null || !List.of("http", "https").contains(uri.getScheme())
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("OSRM base URL must use HTTP(S)");
            }
        }
        this.client = this.baseUrl.isEmpty() ? null : builder.build();
    }

    private static JdkClientHttpRequestFactory defaultRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(2));
        return factory;
    }

    @Override
    public Map<Long, RouteMetrics> findRoutes(BigDecimal latitude, BigDecimal longitude,
            Collection<CatalogStoreSummary> stores) {
        if (client == null || baseUrl.isEmpty() || latitude == null || longitude == null
                || stores == null || stores.isEmpty()) {
            return Map.of();
        }
        List<CatalogStoreSummary> destinations = stores.stream()
                .filter(store -> store.latitude() != null && store.longitude() != null).toList();
        if (destinations.isEmpty()) return Map.of();

        String coordinates = coordinate(longitude, latitude) + ";"
                + destinations.stream().map(store -> coordinate(store.longitude(), store.latitude()))
                        .collect(Collectors.joining(";"));
        String destinationIndexes = IntStream.rangeClosed(1, destinations.size())
                .mapToObj(String::valueOf).collect(Collectors.joining(";"));
        URI requestUri = URI.create(baseUrl + "/table/v1/driving/" + coordinates
                + "?sources=0&destinations=" + destinationIndexes + "&annotations=distance,duration");
        try {
            JsonNode response = client.get().uri(requestUri).retrieve().body(JsonNode.class);
            if (response == null || !"Ok".equals(response.path("code").asText())) {
                log.warn("OSRM returned no route metrics; using straight-line distances");
                return Map.of();
            }
            JsonNode distances = response.path("distances").path(0);
            JsonNode durations = response.path("durations").path(0);
            Map<Long, RouteMetrics> result = new HashMap<>();
            for (int index = 0; index < destinations.size(); index++) {
                JsonNode distance = distances.path(index);
                JsonNode duration = durations.path(index);
                if (!distance.isNumber() || !duration.isNumber()
                        || distance.doubleValue() < 0 || duration.doubleValue() < 0) continue;
                result.put(destinations.get(index).id(), new RouteMetrics(
                        distance.doubleValue() / 1000, duration.doubleValue() / 60));
            }
            return Map.copyOf(result);
        } catch (RestClientException ex) {
            log.warn("OSRM request failed; using straight-line distances ({})",
                    ex.getClass().getSimpleName());
            return Map.of();
        }
    }

    private String coordinate(BigDecimal longitude, BigDecimal latitude) {
        return longitude.toPlainString() + "," + latitude.toPlainString();
    }
}
