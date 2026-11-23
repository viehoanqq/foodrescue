package com.foodrescue.store.application.port.out;

import com.foodrescue.store.domain.model.ReviewView;

public interface IReviewRepository {
    ReviewView.Item save(Long storeId, Long customerId, ReviewView.Create review);
    ReviewView.Page findByStoreId(Long storeId, int page, int size);
}
