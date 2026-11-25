package com.foodrescue.order.infrastructure.adapter.out.persistence;

import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
    List<OrderItemEntity> findByOrderId(Long orderId);
    List<OrderItemEntity> findByBatchId(Long batchId);
}
