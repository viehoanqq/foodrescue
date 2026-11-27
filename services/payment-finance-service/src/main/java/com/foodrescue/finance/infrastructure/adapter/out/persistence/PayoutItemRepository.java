package com.foodrescue.finance.infrastructure.adapter.out.persistence;

import com.foodrescue.finance.infrastructure.adapter.out.persistence.entity.*;

import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface PayoutItemRepository extends JpaRepository<PayoutItemEntity, PayoutItemEntityId> {

    List<PayoutItemEntity> findByPayoutId(long payoutId);
}
