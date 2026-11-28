package com.foodrescue.catalog.application.port.out;

import com.foodrescue.catalog.domain.model.CatalogStoreSummary;
import com.foodrescue.catalog.domain.model.RouteMetrics;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

public interface IRouteDistancePort {
    Map<Long, RouteMetrics> findRoutes(BigDecimal latitude, BigDecimal longitude,
                                       Collection<CatalogStoreSummary> stores);
}
