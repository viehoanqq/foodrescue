package com.foodrescue.common.repository;

import com.foodrescue.common.entity.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderId(Long orderId);

    List<Review> findByStoreIdOrderByCreatedAtDesc(Long storeId);
}
