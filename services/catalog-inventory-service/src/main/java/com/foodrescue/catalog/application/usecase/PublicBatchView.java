package com.foodrescue.catalog.application.usecase;

import com.foodrescue.catalog.domain.model.CatalogStoreSummary;
import com.foodrescue.catalog.domain.model.Category;
import com.foodrescue.catalog.domain.model.RescueBatch;
import com.foodrescue.catalog.domain.model.RouteMetrics;

public record PublicBatchView(RescueBatch batch, Category category, CatalogStoreSummary store,
                              RouteMetrics route) {}
