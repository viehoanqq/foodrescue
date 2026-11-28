package com.foodrescue.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.foodrescue.catalog.application.port.out.ICategoryRepository;
import com.foodrescue.catalog.application.port.out.IPublicBatchRepository;
import com.foodrescue.catalog.application.port.out.IRouteDistancePort;
import com.foodrescue.catalog.application.port.out.IStoreDirectoryPort;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.domain.model.CatalogStoreSummary;
import com.foodrescue.catalog.domain.model.RescueBatch;
import com.foodrescue.catalog.domain.model.RouteMetrics;
import com.foodrescue.catalog.domain.model.enums.BatchStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PublicBatchUseCaseTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final IPublicBatchRepository batches = mock(IPublicBatchRepository.class);
    private final ICategoryRepository categories = mock(ICategoryRepository.class);
    private final IStoreDirectoryPort stores = mock(IStoreDirectoryPort.class);
    private final IRouteDistancePort routes = mock(IRouteDistancePort.class);
    private final PublicBatchUseCase useCase = new PublicBatchUseCase(batches, categories, stores, routes);

    @Test
    void searchIncludesApprovedStoreNameMatchesAndUsesCurrentStoreDirectory() {
        CatalogStoreSummary store = new CatalogStoreSummary(10L, "Bếp Xanh", null, null, "Quận 1",
                null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.valueOf(4.5), 2, null, null, null);
        RescueBatch batch = batch(BatchStatus.AVAILABLE, 3);
        when(stores.findApprovedStores(null, null, null)).thenReturn(List.of(store));
        when(batches.search(anyCollection(), anyCollection(), eq(null), eq(null), eq("Bếp"),
                eq(null), eq(null), eq(null), eq(null), any(LocalDateTime.class),
                eq("newest"), any())).thenReturn(new PageImpl<>(List.of(batch), PageRequest.of(0, 20), 1));

        var result = useCase.search(new PublicBatchFilter(null, null, "Bếp", null, null,
                null, null, null, null, null, "newest"), PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals("Bếp Xanh", result.getContent().get(0).store().name());
        verify(batches).search(eq(List.of(10L)), eq(List.of(10L)), eq(null), eq(null), eq("Bếp"),
                eq(null), eq(null), eq(null), eq(null), any(LocalDateTime.class), eq("newest"), any());
    }

    @Test
    void publicDetailAllowsVisibleSoldOutBatchButNeverAnExpiredOne() {
        when(batches.findById(5L)).thenReturn(Optional.of(batch(BatchStatus.SOLD_OUT, 0)));
        when(stores.findApprovedStore(10L)).thenReturn(Optional.of(new CatalogStoreSummary(10L, "Bếp Xanh",
                null, null, "Quận 1", null, BigDecimal.TEN, BigDecimal.TEN, null, 0, null, null, null)));

        var result = useCase.get(5L, null, null);

        assertEquals(BatchStatus.SOLD_OUT, result.batch().status());
        verify(stores).findApprovedStore(10L);
    }

    @Test
    void detailUsesConfiguredRouteMetricsWhenAvailable() {
        CatalogStoreSummary store = new CatalogStoreSummary(10L, "Bếp Xanh", null, null, "Quận 1",
                null, BigDecimal.TEN, BigDecimal.TEN, null, 0, null, null, null);
        when(batches.findById(5L)).thenReturn(Optional.of(batch(BatchStatus.AVAILABLE, 1)));
        when(stores.findApprovedStore(10L)).thenReturn(Optional.of(store));
        when(routes.findRoutes(eq(BigDecimal.ONE), eq(BigDecimal.ONE), anyCollection()))
                .thenReturn(java.util.Map.of(10L, new RouteMetrics(2.4, 8.5)));

        var result = useCase.get(5L, BigDecimal.ONE, BigDecimal.ONE);

        assertEquals(2.4, result.route().distanceKm());
        assertEquals(8.5, result.route().travelMinutes());
    }

    @Test
    void multiDayBatchIncludesStoreOpeningHoursForDailyPickupDisplay() {
        CatalogStoreSummary summary = new CatalogStoreSummary(10L, "Bếp Xanh", null, null, "Quận 1",
                null, BigDecimal.TEN, BigDecimal.TEN, null, 0, null, null, null);
        CatalogStoreSummary details = new CatalogStoreSummary(10L, "Bếp Xanh", null, null, "Quận 1",
                null, BigDecimal.TEN, BigDecimal.TEN, null, 0, null,
                LocalTime.of(8, 0), LocalTime.of(22, 0));
        LocalDateTime start = now().plusDays(1).withHour(9).withMinute(0);
        LocalDateTime end = start.plusDays(4).withHour(18);
        RescueBatch multiDayBatch = new RescueBatch(5L, 10L, 2L, "Túi", "Mô tả", null,
                BigDecimal.valueOf(100), BigDecimal.valueOf(50), 3, 2, start, end,
                BatchStatus.AVAILABLE, now(), 1, false);
        when(stores.findApprovedStores(null, null, null)).thenReturn(List.of(summary));
        when(stores.findApprovedStore(10L)).thenReturn(Optional.of(details));
        when(batches.search(anyCollection(), anyCollection(), eq(null), eq(null), eq(null),
                eq(null), eq(null), eq(null), eq(null), any(LocalDateTime.class),
                eq("newest"), any())).thenReturn(new PageImpl<>(List.of(multiDayBatch)));

        var result = useCase.search(new PublicBatchFilter(null, null, null, null, null,
                null, null, null, null, null, "newest"), PageRequest.of(0, 20));

        assertEquals(LocalTime.of(8, 0), result.getContent().get(0).store().openTime());
        assertEquals(LocalTime.of(22, 0), result.getContent().get(0).store().closeTime());
    }

    @Test
    void detailDoesNotExposeExpiredBatchOrCallStoreService() {
        when(batches.findById(6L)).thenReturn(Optional.of(new RescueBatch(6L, 10L, 2L,
                "Hết hạn", null, null, BigDecimal.valueOf(100), BigDecimal.valueOf(50), 2, 1,
                now().minusHours(2), now().minusHours(1), BatchStatus.AVAILABLE,
                now().minusDays(1), 1, false)));

        CatalogException error = assertThrows(CatalogException.class, () -> useCase.get(6L, null, null));

        assertEquals("BATCH_NOT_FOUND", error.code());
        verifyNoInteractions(stores);
    }

    @Test
    void invalidPriceRangeIsRejectedBeforeStoreLookup() {
        PublicBatchFilter filter = new PublicBatchFilter(null, null, null,
                BigDecimal.valueOf(100), BigDecimal.TEN, null, null, null, null, null, "newest");

        CatalogException error = assertThrows(CatalogException.class,
                () -> useCase.search(filter, PageRequest.of(0, 20)));

        assertEquals("VALIDATION_ERROR", error.code());
        verifyNoInteractions(stores, batches);
    }

    private RescueBatch batch(BatchStatus status, int remaining) {
        return new RescueBatch(5L, 10L, 2L, "Túi", "Mô tả", null,
                BigDecimal.valueOf(100), BigDecimal.valueOf(50), 3, remaining,
                now().plusMinutes(30), now().plusHours(2), status, now(), 1, false);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }
}
