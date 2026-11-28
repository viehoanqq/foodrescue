package com.foodrescue.catalog.api;

import com.foodrescue.catalog.application.usecase.PublicBatchFilter;
import com.foodrescue.catalog.application.usecase.PublicBatchUseCase;
import com.foodrescue.catalog.application.usecase.PublicBatchView;
import com.foodrescue.catalog.domain.model.CatalogStoreSummary;
import com.foodrescue.catalog.domain.model.Category;
import com.foodrescue.catalog.domain.model.RescueBatch;
import com.foodrescue.catalog.domain.model.RouteMetrics;
import com.foodrescue.catalog.domain.exception.CatalogException;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Khám phá túi giải cứu", description = "Danh sách túi đang bán từ các cửa hàng đã duyệt")
public class PublicBatchController {
    private final PublicBatchUseCase batches;

    public PublicBatchController(PublicBatchUseCase batches) {
        this.batches = batches;
    }

    @GetMapping("/api/batches")
    public PageResponse<BatchResponse> list(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime pickupStartAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime pickupEndBefore,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lon,
            @RequestParam(required = false) BigDecimal radiusKm,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw CatalogException.badRequest("VALIDATION_ERROR", "Trang hoặc kích thước trang không hợp lệ");
        }
        PublicBatchFilter filter = new PublicBatchFilter(storeId, categoryId, keyword, minPrice, maxPrice,
                pickupStartAfter, pickupEndBefore, lat, lon, radiusKm, sort);
        return PageResponse.from(batches.search(filter, PageRequest.of(page, size))
                .map(BatchResponse::from));
    }

    @GetMapping("/api/batches/{id}")
    public BatchResponse get(@PathVariable Long id,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lon) {
        return BatchResponse.from(batches.get(id, lat, lon));
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T> PageResponse<T> from(Page<T> result) {
            return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                    result.getTotalElements(), result.getTotalPages());
        }
    }

    public record BatchResponse(Long id, Long storeId, Long categoryId, String title, String description,
            String imageUrl, BigDecimal originalPrice, BigDecimal rescuePrice, int quantity,
            int remainingQuantity, LocalDateTime pickupStart, LocalDateTime pickupEnd, String status,
            LocalDateTime createdAt, CategoryResponse category, StoreResponse store) {
        static BatchResponse from(PublicBatchView view) {
            RescueBatch batch = view.batch();
            return new BatchResponse(batch.id(), batch.storeId(), batch.categoryId(), batch.title(),
                    batch.description(), batch.imageUrl(), batch.originalPrice(), batch.rescuePrice(),
                    batch.quantity(), batch.remainingQuantity(), batch.pickupStart(), batch.pickupEnd(),
                    batch.status() != null ? batch.status().name() : null, batch.createdAt(), CategoryResponse.from(view.category()),
                    StoreResponse.from(view.store(), view.route()));
        }
    }

    public record CategoryResponse(Long id, String name, String description, String imageUrl) {
        static CategoryResponse from(Category category) {
            return category == null ? null : new CategoryResponse(category.id(), category.name(),
                    category.description(), category.imageUrl());
        }
    }

    public record StoreResponse(Long id, String name, String description, String phone, String address,
            String imageUrl, BigDecimal latitude, BigDecimal longitude, BigDecimal rating,
            long reviewCount, java.time.LocalTime openTime, java.time.LocalTime closeTime,
            Double distanceKm, Double travelMinutes, String distanceSource) {
        static StoreResponse from(CatalogStoreSummary store, RouteMetrics route) {
            Double distance = route == null ? store.distanceKm() : route.distanceKm();
            Double minutes = route == null ? null : route.travelMinutes();
            String source = distance == null ? null : route == null ? "STRAIGHT_LINE" : "OSRM";
            return new StoreResponse(store.id(), store.name(), store.description(), store.phone(),
                    store.address(), store.imageUrl(), store.latitude(), store.longitude(),
                    store.rating(), store.reviewCount(), store.openTime(), store.closeTime(),
                    distance, minutes, source);
        }
    }
}
