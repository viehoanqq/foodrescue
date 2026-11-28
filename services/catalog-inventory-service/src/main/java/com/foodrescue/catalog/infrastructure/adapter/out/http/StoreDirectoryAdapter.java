package com.foodrescue.catalog.infrastructure.adapter.out.http;

import com.foodrescue.buildingblocks.http.InternalServiceClient;
import com.foodrescue.buildingblocks.http.InternalServiceException;
import com.foodrescue.catalog.application.port.out.IStoreDirectoryPort;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.domain.model.CatalogStoreSummary;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class StoreDirectoryAdapter implements IStoreDirectoryPort {
    private static final int PAGE_SIZE = 100;
    private final InternalServiceClient storeClient;

    public StoreDirectoryAdapter(@Value("${app.store-service-url}") String storeUrl,
            @Value("${app.internal-api-key}") String internalKey) {
        this.storeClient = new InternalServiceClient(URI.create(storeUrl), internalKey);
    }

    @Override
    public List<CatalogStoreSummary> findApprovedStores(BigDecimal latitude, BigDecimal longitude,
            BigDecimal radiusKm) {
        List<CatalogStoreSummary> result = new ArrayList<>();
        int page = 0;
        int totalPages;
        do {
            StorePage response = getPage(latitude, longitude, radiusKm, page);
            if (response == null || response.content() == null || response.page() != page
                    || response.totalPages() < 0 || response.totalPages() > 10_000) {
                throw dependencyUnavailable("Store service trả danh sách cửa hàng không hợp lệ");
            }
            result.addAll(response.content().stream().map(this::toSummary).toList());
            totalPages = Math.toIntExact(response.totalPages());
            page++;
        } while (page < totalPages);
        return List.copyOf(result);
    }

    @Override
    public Optional<CatalogStoreSummary> findApprovedStore(Long storeId) {
        try {
            StoreDetail response = storeClient.get("/api/stores/" + storeId, UUID.randomUUID(),
                    StoreDetail.class);
            if (response == null || response.id() == null || !storeId.equals(response.id())
                    || response.name() == null) {
                throw dependencyUnavailable("Store service trả thông tin cửa hàng không hợp lệ");
            }
            return Optional.of(new CatalogStoreSummary(response.id(), response.name(), response.description(),
                    response.phone(), response.address(), response.imageUrl(), response.latitude(),
                    response.longitude(), response.rating(), response.reviewCount(), null,
                    response.openTime(), response.closeTime()));
        } catch (InternalServiceException ex) {
            if (ex.status() == 404) return Optional.empty();
            throw dependencyUnavailable("Không thể tra cứu cửa hàng từ Store service");
        } catch (RestClientException ex) {
            throw dependencyUnavailable("Không thể kết nối Store service");
        }
    }

    private StorePage getPage(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm, int page) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/api/stores")
                .queryParam("page", page).queryParam("size", PAGE_SIZE);
        if (latitude != null) {
            uri.queryParam("lat", latitude.toPlainString()).queryParam("lon", longitude.toPlainString());
        }
        if (radiusKm != null) uri.queryParam("radiusKm", radiusKm.toPlainString());
        try {
            return storeClient.get(uri.build().encode().toUriString(), UUID.randomUUID(), StorePage.class);
        } catch (InternalServiceException ex) {
            throw dependencyUnavailable("Không thể lấy danh sách cửa hàng từ Store service");
        } catch (RestClientException ex) {
            throw dependencyUnavailable("Không thể kết nối Store service");
        }
    }

    private CatalogStoreSummary toSummary(StoreSummary response) {
        if (response == null || response.id() == null || response.name() == null
                || response.latitude() == null || response.longitude() == null) {
            throw dependencyUnavailable("Store service trả dữ liệu cửa hàng không hợp lệ");
        }
        return new CatalogStoreSummary(response.id(), response.name(), null, null, response.address(),
                response.imageUrl(), response.latitude(), response.longitude(), response.rating(),
                response.reviewCount(), response.distanceKm(), null, null);
    }

    private CatalogException dependencyUnavailable(String message) {
        return new CatalogException(503, "DEPENDENCY_UNAVAILABLE", message);
    }

    private record StorePage(List<StoreSummary> content, int page, int size, long totalElements,
                             long totalPages) {}
    private record StoreSummary(Long id, String name, String address, String imageUrl, BigDecimal latitude,
                                BigDecimal longitude, BigDecimal rating, long reviewCount, Double distanceKm) {}
    private record StoreDetail(Long id, String name, String description, String phone, String address,
                               String imageUrl, BigDecimal latitude, BigDecimal longitude, BigDecimal rating,
                               long reviewCount, java.time.LocalTime openTime, java.time.LocalTime closeTime) {}
}
