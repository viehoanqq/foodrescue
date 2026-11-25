package com.foodrescue.order.infrastructure.adapter.out.persistence;

import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.CartItemEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.CartItemEntityId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCartItemRepository extends JpaRepository<CartItemEntity, CartItemEntityId> {
    List<CartItemEntity> findByCustomerId(Long customerId);
    void deleteByCustomerId(Long customerId);
    void deleteByCustomerIdAndBatchId(Long customerId, Long batchId);
}
