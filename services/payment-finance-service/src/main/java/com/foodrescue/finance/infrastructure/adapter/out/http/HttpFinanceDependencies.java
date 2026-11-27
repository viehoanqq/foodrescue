package com.foodrescue.finance.infrastructure.adapter.out.http;

import com.foodrescue.buildingblocks.http.InternalServiceClient;
import com.foodrescue.finance.application.port.out.FinanceDependencies;
import com.foodrescue.finance.domain.exception.FinanceException;
import com.foodrescue.finance.domain.model.FinanceModels.*;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

public class HttpFinanceDependencies implements FinanceDependencies {
    private final InternalServiceClient orders, stores, identity;
    private final RestClient publicStore;

    public HttpFinanceDependencies(
            String orderUrl, String storeUrl, String identityUrl, String key, String financeKey) {
        orders = new InternalServiceClient(URI.create(orderUrl), key);
        stores = new InternalServiceClient(URI.create(storeUrl), financeKey);
        identity = new InternalServiceClient(URI.create(identityUrl), key);
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        publicStore = RestClient.builder().baseUrl(storeUrl).requestFactory(factory).build();
        storeCounts = new InternalServiceClient(URI.create(storeUrl), key);
    }

    private final InternalServiceClient storeCounts;

    public Intent intent(long order, UUID correlation) {
        return call(
                () ->
                        orders.get(
                                "/internal/orders/" + order + "/payment-intent",
                                correlation,
                                Intent.class));
    }

    public Account account(long store, UUID correlation) {
        return call(
                () ->
                        stores.get(
                                "/internal/stores/" + store + "/payout-account",
                                correlation,
                                Account.class));
    }

    public long ownerStore(String authorization) {
        return call(
                () -> {
                    var result =
                            publicStore
                                    .get()
                                    .uri("/api/store/profile")
                                    .header("Authorization", authorization)
                                    .retrieve()
                                    .body(Map.class);
                    if (result == null || !(result.get("id") instanceof Number))
                        throw new IllegalStateException();
                    return ((Number) result.get("id")).longValue();
                });
    }

    public Map<String, Object> counts(UUID correlation) {
        return call(
                () ->
                        Map.of(
                                "users",
                                identity.get("/internal/users/count", correlation, Map.class),
                                "stores",
                                storeCounts.get("/internal/stores/count", correlation, Map.class)));
    }

    private <T> T call(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException ex) {
            throw new FinanceException(
                    503, "DEPENDENCY_UNAVAILABLE", "Dịch vụ liên quan chưa sẵn sàng");
        }
    }
}
