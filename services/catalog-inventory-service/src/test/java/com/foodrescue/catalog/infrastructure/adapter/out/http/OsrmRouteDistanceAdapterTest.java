package com.foodrescue.catalog.infrastructure.adapter.out.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.foodrescue.catalog.domain.model.CatalogStoreSummary;
import com.foodrescue.catalog.domain.model.RouteMetrics;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OsrmRouteDistanceAdapterTest {
    @Test
    void mapsZeroBasedOsrmTableCellsForSingleAndMultipleDestinations() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsrmRouteDistanceAdapter adapter = new OsrmRouteDistanceAdapter("http://osrm.local", builder);

        server.expect(requestTo(
                "http://osrm.local/table/v1/driving/106.68,10.76;106.69,10.77?sources=0&destinations=1&annotations=distance,duration"))
                .andRespond(withSuccess(
                        "{\"code\":\"Ok\",\"distances\":[[2400.0]],\"durations\":[[510.0]]}",
                        MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                "http://osrm.local/table/v1/driving/106.68,10.76;106.69,10.77;106.71,10.79?sources=0&destinations=1;2&annotations=distance,duration"))
                .andRespond(withSuccess(
                        "{\"code\":\"Ok\",\"distances\":[[1500.0,3250.0]],\"durations\":[[180.0,420.0]]}",
                        MediaType.APPLICATION_JSON));

        CatalogStoreSummary first = store(10L, "10.77", "106.69");
        CatalogStoreSummary second = store(20L, "10.79", "106.71");

        Map<Long, RouteMetrics> single = adapter.findRoutes(
                new BigDecimal("10.76"), new BigDecimal("106.68"), List.of(first));
        assertEquals(1, single.size());
        assertEquals(2.4, single.get(10L).distanceKm(), 1e-6);
        assertEquals(8.5, single.get(10L).travelMinutes(), 1e-6);

        Map<Long, RouteMetrics> multi = adapter.findRoutes(
                new BigDecimal("10.76"), new BigDecimal("106.68"), List.of(first, second));
        assertEquals(2, multi.size());
        assertEquals(1.5, multi.get(10L).distanceKm(), 1e-6);
        assertEquals(3.0, multi.get(10L).travelMinutes(), 1e-6);
        assertEquals(3.25, multi.get(20L).distanceKm(), 1e-6);
        assertEquals(7.0, multi.get(20L).travelMinutes(), 1e-6);
        server.verify();
    }

    @Test
    void skipsNullCellsAndFallsBackCleanlyWhenOsrmFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsrmRouteDistanceAdapter adapter = new OsrmRouteDistanceAdapter("http://osrm.local", builder);

        server.expect(requestTo(
                "http://osrm.local/table/v1/driving/106.68,10.76;106.69,10.77;106.71,10.79?sources=0&destinations=1;2&annotations=distance,duration"))
                .andRespond(withSuccess(
                        "{\"code\":\"Ok\",\"distances\":[[null,2000.0]],\"durations\":[[null,240.0]]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(
                "http://osrm.local/table/v1/driving/106.68,10.76;106.69,10.77?sources=0&destinations=1&annotations=distance,duration"))
                .andRespond(withServerError());

        Map<Long, RouteMetrics> partial = adapter.findRoutes(
                new BigDecimal("10.76"), new BigDecimal("106.68"),
                List.of(store(10L, "10.77", "106.69"), store(20L, "10.79", "106.71")));
        assertNull(partial.get(10L));
        assertEquals(2.0, partial.get(20L).distanceKm(), 1e-6);
        assertEquals(4.0, partial.get(20L).travelMinutes(), 1e-6);

        assertTrue(adapter.findRoutes(
                new BigDecimal("10.76"), new BigDecimal("106.68"),
                List.of(store(10L, "10.77", "106.69"))).isEmpty());
        assertTrue(new OsrmRouteDistanceAdapter("").findRoutes(
                BigDecimal.ONE, BigDecimal.ONE, List.of(store(10L, "10.77", "106.69"))).isEmpty());
        server.verify();
    }

    private static CatalogStoreSummary store(Long id, String lat, String lon) {
        return new CatalogStoreSummary(id, "Store " + id, null, null, null, null,
                new BigDecimal(lat), new BigDecimal(lon), null, 0L, null, null, null);
    }
}
