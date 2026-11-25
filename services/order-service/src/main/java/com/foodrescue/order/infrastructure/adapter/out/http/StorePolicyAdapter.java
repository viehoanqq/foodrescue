package com.foodrescue.order.infrastructure.adapter.out.http;

import com.foodrescue.buildingblocks.http.InternalServiceClient;
import com.foodrescue.order.application.port.out.IStorePolicyPort;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StorePolicyAdapter implements IStorePolicyPort {
    private static final Logger log = LoggerFactory.getLogger(StorePolicyAdapter.class);
    private final InternalServiceClient client;

    public StorePolicyAdapter(@Value("${app.store-service-url:http://store-service:8083}") String url,
                              @Value("${app.internal-api-key:}") String key) {
        this.client = new InternalServiceClient(URI.create(url), key);
    }

    @Override
    public StoreCheckoutPolicy getPolicy(Long storeId) {
        try {
            return client.get("/internal/stores/" + storeId + "/checkout-policy", UUID.randomUUID(), StoreCheckoutPolicy.class);
        } catch (Exception ex) {
            log.warn("Cannot fetch store policy for storeId={}, using default APPROVED with 15% commission: {}", storeId, ex.getMessage());
            return new StoreCheckoutPolicy(storeId, "APPROVED", new BigDecimal("15.00"));
        }
    }
}
