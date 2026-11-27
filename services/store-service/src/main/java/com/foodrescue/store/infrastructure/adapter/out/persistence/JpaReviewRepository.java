package com.foodrescue.store.infrastructure.adapter.out.persistence;

import com.foodrescue.store.application.port.out.IReviewRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.ReviewView;
import com.foodrescue.store.infrastructure.adapter.out.persistence.entity.ReviewEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaReviewRepository implements IReviewRepository {
    private final IJpaReviewRepository reviews;

    public JpaReviewRepository(IJpaReviewRepository reviews) { this.reviews = reviews; }

    @Override
    @Transactional
    public ReviewView.Item save(Long storeId, Long customerId, ReviewView.Create review) {
        ReviewEntity entity = new ReviewEntity();
        entity.setStoreId(storeId);
        entity.setCustomerId(customerId);
        entity.setOrderId(review.orderId());
        entity.setRating(review.rating().byteValue());
        entity.setComment(review.comment());
        entity.setCreatedAt(LocalDateTime.now());
        try {
            return item(reviews.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            throw new StoreException(409, "REVIEW_EXISTS", "Đơn hàng này đã được đánh giá");
        }
    }

    @Override
    public ReviewView.Page findByStoreId(Long storeId, int page, int size) {
        var result = reviews.findByStoreId(storeId, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        Double average = reviews.averageRating(storeId);
        return new ReviewView.Page(result.map(this::item).getContent(),
                average == null ? BigDecimal.ZERO : BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    private ReviewView.Item item(ReviewEntity e) {
        return new ReviewView.Item(e.getId(), e.getOrderId(), e.getRating().intValue(),
                e.getComment(), e.getCreatedAt());
    }
}
