package com.foodrescue.catalog.application.port.out;

import com.foodrescue.catalog.domain.model.RescueBatch;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IPublicBatchRepository {
    Page<RescueBatch> search(Collection<Long> approvedStoreIds, Collection<Long> storeNameMatches,
            Long storeId, Long categoryId, String keyword, BigDecimal minPrice, BigDecimal maxPrice,
            LocalDateTime pickupStartAfter, LocalDateTime pickupEndBefore, LocalDateTime now,
            String sort, Pageable pageable);

    Optional<RescueBatch> findById(Long batchId);
}
