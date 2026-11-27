package com.foodrescue.order.infrastructure.adapter.out.http;

import com.foodrescue.buildingblocks.http.InternalServiceClient;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.domain.model.StockReservation;
import com.foodrescue.order.domain.model.StockReservationCommand;
import java.net.URI;
import java.util.UUID;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CatalogInventoryAdapter implements IInventoryPort {
    private final InternalServiceClient client;
    private record CheckoutStoreRequest(List<Long> batchIds) {}
    private record CheckoutStoreResponse(Long storeId) {}

    public CatalogInventoryAdapter(@Value("${app.catalog-service-url}") String url,
                                   @Value("${app.internal-api-key}") String key) {
        this.client = new InternalServiceClient(URI.create(url), key);
    }

    @Override
    public Long requireSingleStore(List<Long> batchIds) {
        return client.post("/internal/batches/checkout-store", new CheckoutStoreRequest(batchIds),
                UUID.randomUUID(), CheckoutStoreResponse.class).storeId();
    }

    @Override
    public StockReservation reserve(StockReservationCommand command, UUID correlationId) {
        return client.post("/internal/reservations", command, correlationId, StockReservation.class);
    }

    @Override
    public StockReservation find(UUID reservationId, UUID correlationId) {
        return client.get("/internal/reservations/" + reservationId, correlationId, StockReservation.class);
    }

    @Override
    public StockReservation release(UUID reservationId, UUID correlationId) {
        return client.post("/internal/reservations/" + reservationId + "/release", correlationId, StockReservation.class);
    }
}
