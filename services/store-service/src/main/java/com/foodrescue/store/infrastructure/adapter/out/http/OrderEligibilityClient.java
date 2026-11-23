package com.foodrescue.store.infrastructure.adapter.out.http;

import com.foodrescue.buildingblocks.http.InternalServiceClient;
import com.foodrescue.buildingblocks.http.InternalServiceException;
import com.foodrescue.store.application.port.out.IOrderEligibilityPort;
import com.foodrescue.store.domain.exception.StoreException;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class OrderEligibilityClient implements IOrderEligibilityPort {
    private final InternalServiceClient client;

    public OrderEligibilityClient(@Value("${app.order-service-url}") String orderUrl,
                                  @Value("${app.internal-api-key}") String internalKey) {
        this.client = new InternalServiceClient(URI.create(orderUrl), internalKey);
    }

    @Override
    public Eligibility check(Long orderId, Long customerId) {
        try {
            Eligibility result = client.get("/internal/orders/" + orderId
                    + "/review-eligibility?customerId=" + customerId, UUID.randomUUID(), Eligibility.class);
            if (result == null) throw new StoreException(502, "ORDER_ERROR", "Order không trả kết quả xác minh đơn");
            return result;
        } catch (InternalServiceException ex) {
            throw new StoreException(502, "ORDER_ERROR", "Order không xác minh được đơn hàng");
        } catch (RestClientException ex) {
            throw new StoreException(503, "ORDER_UNAVAILABLE", "Không kết nối được Order");
        }
    }
}
