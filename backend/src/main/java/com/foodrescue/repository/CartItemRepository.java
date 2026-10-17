package com.foodrescue.repository;

import com.foodrescue.entity.CartItem;
import com.foodrescue.entity.CartItemId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {

    List<CartItem> findByCustomerId(Long customerId);
}
