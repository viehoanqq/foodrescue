package com.foodrescue.catalog.application.usecase;

import com.foodrescue.catalog.application.port.out.ICategoryRepository;
import com.foodrescue.catalog.application.port.out.IPublicBatchRepository;
import com.foodrescue.catalog.application.port.out.IRouteDistancePort;
import com.foodrescue.catalog.application.port.out.IStoreDirectoryPort;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.domain.model.CatalogStoreSummary;
import com.foodrescue.catalog.domain.model.Category;
import com.foodrescue.catalog.domain.model.RescueBatch;
import com.foodrescue.catalog.domain.model.RouteMetrics;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicBatchUseCase {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> SORTS = Set.of("newest", "price_asc", "price_desc",
            "ending_soon", "discount_desc");

    private final IPublicBatchRepository batches;
    private final ICategoryRepository categories;
    private final IStoreDirectoryPort stores;
    private final IRouteDistancePort routes;

    public PublicBatchUseCase(IPublicBatchRepository batches, ICategoryRepository categories,
            IStoreDirectoryPort stores, IRouteDistancePort routes) {
        this.batches = batches;
        this.categories = categories;
        this.stores = stores;
        this.routes = routes;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Page<PublicBatchView> search(PublicBatchFilter filter, Pageable pageable) {
        validate(filter, pageable);
        List<CatalogStoreSummary> approvedStores;
        if (filter.storeId() == null || filter.latitude() != null) {
            approvedStores = stores.findApprovedStores(filter.latitude(), filter.longitude(), filter.radiusKm());
            if (filter.storeId() != null) {
                approvedStores = approvedStores.stream()
                        .filter(store -> filter.storeId().equals(store.id())).toList();
            }
        } else {
            approvedStores = stores.findApprovedStore(filter.storeId()).map(List::of).orElseGet(List::of);
        }
        if (approvedStores.isEmpty()) return Page.empty(pageable);

        String keyword = cleanKeyword(filter.keyword());
        List<Long> approvedStoreIds = approvedStores.stream().map(CatalogStoreSummary::id).toList();
        List<Long> storeNameMatches = keyword == null ? List.of() : approvedStores.stream()
                .filter(store -> store.name().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
                .map(CatalogStoreSummary::id).toList();
        Page<RescueBatch> result = batches.search(approvedStoreIds, storeNameMatches, filter.storeId(),
                filter.categoryId(), keyword, filter.minPrice(), filter.maxPrice(),
                filter.pickupStartAfter(), filter.pickupEndBefore(), LocalDateTime.now(VIETNAM),
                filter.sort(), pageable);
        return mapPage(result, approvedStores, filter.latitude(), filter.longitude());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PublicBatchView get(Long batchId, BigDecimal latitude, BigDecimal longitude) {
        validateLocation(latitude, longitude, null);
        if (batchId == null || batchId < 1) throw invalid("batchId phải lớn hơn 0");
        LocalDateTime now = LocalDateTime.now(VIETNAM);
        RescueBatch batch = batches.findById(batchId).filter(row -> isPublic(row, now))
                .orElseThrow(this::notFound);
        CatalogStoreSummary store = stores.findApprovedStore(batch.storeId()).orElseThrow(this::notFound);
        if (latitude != null) {
            store = new CatalogStoreSummary(store.id(), store.name(), store.description(), store.phone(),
                    store.address(), store.imageUrl(), store.latitude(), store.longitude(), store.rating(),
                    store.reviewCount(), distanceKm(latitude, longitude, store.latitude(), store.longitude()),
                    store.openTime(), store.closeTime());
        }
        Category category = categories.findByStore(batch.storeId()).stream()
                .filter(row -> row.id().equals(batch.categoryId())).findFirst().orElse(null);
        Map<Long, RouteMetrics> route = latitude == null ? Map.of()
                : routes.findRoutes(latitude, longitude, List.of(store));
        return new PublicBatchView(batch, category, store, route.get(store.id()));
    }

    private Page<PublicBatchView> mapPage(Page<RescueBatch> result, List<CatalogStoreSummary> approvedStores,
            BigDecimal latitude, BigDecimal longitude) {
        Map<Long, CatalogStoreSummary> storeById = approvedStores.stream().collect(
                Collectors.toMap(CatalogStoreSummary::id, Function.identity()));
        List<Long> storeIds = result.getContent().stream().map(RescueBatch::storeId).distinct().toList();
        result.getContent().stream()
                .filter(batch -> !batch.pickupStart().toLocalDate().equals(batch.pickupEnd().toLocalDate()))
                .map(RescueBatch::storeId).distinct()
                .forEach(storeId -> stores.findApprovedStore(storeId).ifPresent(details -> {
                    CatalogStoreSummary summary = storeById.get(storeId);
                    if (summary != null) {
                        storeById.put(storeId, new CatalogStoreSummary(summary.id(), summary.name(),
                                summary.description(), summary.phone(), summary.address(), summary.imageUrl(),
                                summary.latitude(), summary.longitude(), summary.rating(), summary.reviewCount(),
                                summary.distanceKm(), details.openTime(), details.closeTime()));
                    }
                }));
        Map<Long, Category> categoryById = categories.findByStores(storeIds).stream().collect(
                Collectors.toMap(Category::id, Function.identity()));
        List<CatalogStoreSummary> pageStores = storeIds.stream().map(storeById::get)
                .filter(java.util.Objects::nonNull).toList();
        Map<Long, RouteMetrics> routeByStore = latitude == null ? Map.of()
                : routes.findRoutes(latitude, longitude, pageStores);
        List<PublicBatchView> content = result.getContent().stream().map(batch ->
                new PublicBatchView(batch, categoryById.get(batch.categoryId()), storeById.get(batch.storeId()),
                        routeByStore.get(batch.storeId())))
                .toList();
        return new PageImpl<>(content, result.getPageable(), result.getTotalElements());
    }

    private void validate(PublicBatchFilter filter, Pageable pageable) {
        if (filter == null) throw invalid("Bộ lọc không hợp lệ");
        if (filter.storeId() != null && filter.storeId() < 1) throw invalid("storeId phải lớn hơn 0");
        if (filter.categoryId() != null && filter.categoryId() < 1) throw invalid("categoryId phải lớn hơn 0");
        if (filter.minPrice() != null && filter.minPrice().signum() < 0) throw invalid("minPrice không hợp lệ");
        if (filter.maxPrice() != null && filter.maxPrice().signum() < 0) throw invalid("maxPrice không hợp lệ");
        if (filter.minPrice() != null && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw invalid("minPrice không được lớn hơn maxPrice");
        }
        if (filter.pickupStartAfter() != null && filter.pickupEndBefore() != null
                && filter.pickupStartAfter().isAfter(filter.pickupEndBefore())) {
            throw invalid("Khoảng thời gian lọc không hợp lệ");
        }
        validateLocation(filter.latitude(), filter.longitude(), filter.radiusKm());
        if (pageable == null || pageable.getPageNumber() < 0 || pageable.getPageSize() < 1
                || pageable.getPageSize() > 100) throw invalid("Trang hoặc kích thước trang không hợp lệ");
        if (!SORTS.contains(filter.sort())) throw invalid("sort không được hỗ trợ");
        cleanKeyword(filter.keyword());
    }

    private void validateLocation(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm) {
        if ((latitude == null) != (longitude == null)) throw invalid("Cần truyền cả lat và lon");
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) throw invalid("Tọa độ không hợp lệ");
        if (radiusKm != null && (latitude == null || radiusKm.signum() <= 0
                || radiusKm.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw invalid("Bán kính cần tọa độ và phải lớn hơn 0, không quá 100 km");
        }
    }

    private String cleanKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String value = keyword.trim();
        if (value.length() > 100) throw invalid("keyword tối đa 100 ký tự");
        return value;
    }

    private boolean isPublic(RescueBatch batch, LocalDateTime now) {
        return !batch.deleted() && batch.pickupEnd().isAfter(now)
                && (("AVAILABLE".equals(batch.status()) && batch.remainingQuantity() > 0)
                    || ("SOLD_OUT".equals(batch.status()) && batch.remainingQuantity() == 0));
    }

    private Double distanceKm(BigDecimal latitude, BigDecimal longitude, BigDecimal storeLatitude,
            BigDecimal storeLongitude) {
        if (storeLatitude == null || storeLongitude == null) return null;
        double lat1 = Math.toRadians(latitude.doubleValue());
        double lat2 = Math.toRadians(storeLatitude.doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(storeLongitude.doubleValue() - longitude.doubleValue());
        double value = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    private CatalogException notFound() {
        return CatalogException.notFound("BATCH_NOT_FOUND", "Không tìm thấy túi đang bán");
    }

    private CatalogException invalid(String message) {
        return CatalogException.badRequest("VALIDATION_ERROR", message);
    }
}
