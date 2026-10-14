package com.foodrescue.batch;

import com.foodrescue.common.entity.RescueBatch;
import com.foodrescue.common.entity.Review;
import com.foodrescue.common.entity.Store;
import com.foodrescue.common.exception.BusinessException;
import com.foodrescue.common.exception.ErrorCode;
import com.foodrescue.common.repository.RescueBatchRepository;
import com.foodrescue.common.repository.ReviewRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchService {

    private final RescueBatchRepository rescueBatchRepository;
    private final ReviewRepository reviewRepository;

    public BatchService(RescueBatchRepository rescueBatchRepository, ReviewRepository reviewRepository) {
        this.rescueBatchRepository = rescueBatchRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public BatchDetailResponse getBatchDetail(Long id) {
        RescueBatch batch = rescueBatchRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        Store store = batch.getStore();
        List<Review> reviews = reviewRepository.findByStoreIdOrderByCreatedAtDesc(store.getId());

        double avgRating = reviews.isEmpty()
                ? 5.0
                : reviews.stream().mapToInt(Review::getRating).average().orElse(5.0);

        List<BatchDetailResponse.BatchReviewDto> reviewDtos = reviews.stream()
                .limit(10)
                .map(r -> new BatchDetailResponse.BatchReviewDto(
                        r.getId(),
                        r.getCustomer().getFullName(),
                        r.getRating(),
                        r.getComment(),
                        r.getCreatedAt()))
                .toList();

        return new BatchDetailResponse(
                batch.getId(),
                batch.getTitle(),
                batch.getDescription(),
                batch.getImageUrl(),
                batch.getOriginalPrice(),
                batch.getRescuePrice(),
                batch.getQuantity(),
                batch.getRemainingQuantity(),
                batch.getPickupStart(),
                batch.getPickupEnd(),
                batch.getStatus(),
                store.getId(),
                store.getName(),
                store.getAddress(),
                store.getPhone(),
                store.getOpenTime(),
                store.getCloseTime(),
                store.getLatitude(),
                store.getLongitude(),
                Math.round(avgRating * 10.0) / 10.0,
                reviews.size(),
                reviewDtos);
    }
}
