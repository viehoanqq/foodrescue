package com.foodrescue.catalog.infrastructure.adapter.out.http;

import com.foodrescue.buildingblocks.http.InternalServiceClient;
import com.foodrescue.buildingblocks.http.InternalServiceException;
import com.foodrescue.catalog.application.port.out.IStoreOwnerLookupPort;
import com.foodrescue.catalog.application.port.out.IStoreStatusPort;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.domain.model.CatalogStore;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class StoreOwnerLookupAdapter implements IStoreOwnerLookupPort, IStoreStatusPort {
    private final InternalServiceClient storeClient;

    public StoreOwnerLookupAdapter(@Value("${app.store-service-url}") String storeUrl,
            @Value("${app.internal-api-key}") String internalKey) {
        this.storeClient = new InternalServiceClient(URI.create(storeUrl), internalKey);
    }

    @Override
    public Optional<CatalogStore> findByOwnerId(Long ownerId) {
        try {
            StoreOwnerResponse response = storeClient.get("/internal/stores/by-owner/" + ownerId,
                    UUID.randomUUID(), StoreOwnerResponse.class);
            if (response == null || response.storeId() == null || response.ownerId() == null
                    || !ownerId.equals(response.ownerId()) || response.status() == null) {
                throw new CatalogException(503, "DEPENDENCY_UNAVAILABLE",
                        "Store service trả dữ liệu chủ cửa hàng không hợp lệ");
            }
            return Optional.of(new CatalogStore(response.storeId(), ownerId, response.status(),
                    response.version() == null || response.version() < 1 ? 1 : response.version()));
        } catch (InternalServiceException ex) {
            if (ex.status() == 404) return Optional.empty();
            throw new CatalogException(503, "DEPENDENCY_UNAVAILABLE",
                    "Không thể tra cứu cửa hàng từ Store service");
        } catch (RestClientException ex) {
            throw new CatalogException(503, "DEPENDENCY_UNAVAILABLE",
                    "Không thể kết nối Store service");
        }
    }

    @Override
    public Optional<CatalogStore> findByStoreId(Long storeId) {
        try {
            CheckoutPolicyResponse response = storeClient.get("/internal/stores/" + storeId + "/checkout-policy",
                    UUID.randomUUID(), CheckoutPolicyResponse.class);
            if (response == null || response.storeId() == null || response.status() == null) {
                throw new CatalogException(503, "DEPENDENCY_UNAVAILABLE",
                        "Store service trả chính sách cửa hàng không hợp lệ");
            }
            return Optional.of(new CatalogStore(response.storeId(), null, response.status(),
                    response.version() < 1 ? 1 : response.version()));
        } catch (InternalServiceException ex) {
            if (ex.status() == 404) return Optional.empty();
            throw new CatalogException(503, "DEPENDENCY_UNAVAILABLE",
                    "Không thể tra cứu trạng thái cửa hàng từ Store service");
        } catch (RestClientException ex) {
            throw new CatalogException(503, "DEPENDENCY_UNAVAILABLE",
                    "Không thể kết nối Store service");
        }
    }

    private record StoreOwnerResponse(Long storeId, Long ownerId, String status, Long version) {}
    private record CheckoutPolicyResponse(Long storeId, String status, java.math.BigDecimal commissionRate,
                                          long version, java.time.LocalDateTime updatedAt) {}
}
