package com.foodrescue.store.infrastructure.adapter.out.persistence;

import com.foodrescue.store.infrastructure.adapter.out.persistence.entity.ReviewEntity;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IJpaReviewRepository extends JpaRepository<ReviewEntity, Long> {
    Page<ReviewEntity> findByStoreId(Long storeId, Pageable pageable);
    @Query("select avg(r.rating) from ReviewEntity r where r.storeId = :storeId")
    Double averageRating(Long storeId);
}
