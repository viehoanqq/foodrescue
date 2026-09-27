package com.foodrescue.common.repository;

import com.foodrescue.common.entity.CartItem;
import com.foodrescue.common.entity.CartItemId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {

    List<CartItem> findByCustomerId(Long customerId);
}
