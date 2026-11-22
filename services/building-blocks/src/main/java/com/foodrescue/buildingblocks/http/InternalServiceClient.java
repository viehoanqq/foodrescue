package com.foodrescue.buildingblocks.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Runtime building block for calls between FoodRescue services. */
public final class InternalServiceClient {
    private final RestClient rest;
    private final String internalKey;

    public InternalServiceClient(URI baseUrl, String internalKey) {
        if (baseUrl == null || !"http".equalsIgnoreCase(baseUrl.getScheme()) && !"https".equalsIgnoreCase(baseUrl.getScheme())) {
            throw new IllegalArgumentException("Service base URL must use HTTP(S)");
        }
        if (internalKey == null || internalKey.isBlank()) {
            throw new IllegalArgumentException("Internal API key is required");
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.rest = RestClient.builder().baseUrl(baseUrl.toString()).requestFactory(factory).build();
        this.internalKey = internalKey;
    }

    public <T> T get(String path, UUID correlationId, Class<T> responseType) {
        try {
            return rest.get().uri(path).headers(headers -> headers(headers, correlationId))
                    .retrieve().body(responseType);
        } catch (RestClientResponseException ex) {
            throw new InternalServiceException(ex.getStatusCode().value(), ex.getResponseBodyAsString(), ex);
        }
    }

    public <T> T post(String path, Object body, UUID correlationId, Class<T> responseType) {
        try {
            return rest.post().uri(path).headers(headers -> headers(headers, correlationId))
                    .body(body).retrieve().body(responseType);
        } catch (RestClientResponseException ex) {
            throw new InternalServiceException(ex.getStatusCode().value(), ex.getResponseBodyAsString(), ex);
        }
    }

    public <T> T post(String path, UUID correlationId, Class<T> responseType) {
        try {
            return rest.post().uri(path).headers(headers -> headers(headers, correlationId))
                    .retrieve().body(responseType);
        } catch (RestClientResponseException ex) {
            throw new InternalServiceException(ex.getStatusCode().value(), ex.getResponseBodyAsString(), ex);
        }
    }

    private void headers(org.springframework.http.HttpHeaders headers, UUID correlationId) {
        headers.set("X-Internal-Key", internalKey);
        headers.set("X-Correlation-Id", correlationId.toString());
    }
}
