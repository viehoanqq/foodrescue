package com.foodrescue.cart;

import com.foodrescue.common.entity.CartItem;
import com.foodrescue.common.entity.CartItemId;
import com.foodrescue.common.entity.RescueBatch;
import com.foodrescue.common.entity.Store;
import com.foodrescue.common.enums.BatchStatus;
import com.foodrescue.common.exception.BusinessException;
import com.foodrescue.common.exception.ErrorCode;
import com.foodrescue.common.repository.CartItemRepository;
import com.foodrescue.common.repository.RescueBatchRepository;
import com.foodrescue.common.util.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final RescueBatchRepository rescueBatchRepository;

    public CartService(CartItemRepository cartItemRepository, RescueBatchRepository rescueBatchRepository) {
        this.cartItemRepository = cartItemRepository;
        this.rescueBatchRepository = rescueBatchRepository;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long customerId) {
        List<CartItem> items = cartItemRepository.findByCustomerId(customerId);
        if (items.isEmpty()) {
            return new CartResponse(null, null, List.of(), BigDecimal.ZERO, 0, null, null, false, false);
        }

        List<CartItemDto> itemDtos = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalItems = 0;
        Long storeId = null;
        String storeName = null;
        LocalDateTime maxPickupStart = null;
        LocalDateTime minPickupEnd = null;
        boolean hasUnavailable = false;

        LocalDateTime now = LocalDateTime.now();

        for (CartItem ci : items) {
            RescueBatch batch = rescueBatchRepository.findById(ci.getBatchId()).orElse(null);
            if (batch == null) {
                continue;
            }

            Store store = batch.getStore();
            storeId = store.getId();
            storeName = store.getName();

            boolean outOfStock = batch.getStatus() != BatchStatus.AVAILABLE
                    || batch.getRemainingQuantity() < ci.getQuantity()
                    || batch.getPickupEnd().isBefore(now);
            if (outOfStock) {
                hasUnavailable = true;
            }

            BigDecimal subtotal = Money.subtotal(batch.getRescuePrice(), ci.getQuantity());
            totalAmount = totalAmount.add(subtotal);
            totalItems += ci.getQuantity();

            if (maxPickupStart == null || batch.getPickupStart().isAfter(maxPickupStart)) {
                maxPickupStart = batch.getPickupStart();
            }
            if (minPickupEnd == null || batch.getPickupEnd().isBefore(minPickupEnd)) {
                minPickupEnd = batch.getPickupEnd();
            }

            itemDtos.add(new CartItemDto(
                    batch.getId(),
                    ci.getQuantity(),
                    batch.getTitle(),
                    batch.getOriginalPrice(),
                    batch.getRescuePrice(),
                    batch.getImageUrl(),
                    storeId,
                    storeName,
                    batch.getPickupStart(),
                    batch.getPickupEnd(),
                    batch.getRemainingQuantity(),
                    batch.getStatus(),
                    subtotal,
                    outOfStock));
        }

        boolean pickupConflict = maxPickupStart != null && minPickupEnd != null && !minPickupEnd.isAfter(maxPickupStart);

        return new CartResponse(
                storeId,
                storeName,
                itemDtos,
                totalAmount,
                totalItems,
                maxPickupStart,
                minPickupEnd,
                pickupConflict,
                hasUnavailable);
    }

    @Transactional
    public CartResponse addToCart(Long customerId, Long batchId, int quantity) {
        if (quantity < 1 || quantity > 5) {
            throw new BusinessException(ErrorCode.CART_ITEM_LIMIT);
        }

        RescueBatch batch = rescueBatchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (batch.getStatus() != BatchStatus.AVAILABLE || batch.getPickupEnd().isBefore(now)) {
            throw new BusinessException(ErrorCode.BATCH_NOT_AVAILABLE);
        }
        if (batch.getRemainingQuantity() <= 0) {
            throw new BusinessException(ErrorCode.BATCH_SOLD_OUT);
        }

        List<CartItem> currentItems = cartItemRepository.findByCustomerId(customerId);
        for (CartItem ci : currentItems) {
            if (!ci.getBatchId().equals(batchId)) {
                RescueBatch other = rescueBatchRepository.findById(ci.getBatchId()).orElse(null);
                if (other != null && !other.getStore().getId().equals(batch.getStore().getId())) {
                    throw new BusinessException(ErrorCode.CART_OTHER_STORE);
                }
            }
        }

        CartItemId id = new CartItemId(customerId, batchId);
        CartItem item = cartItemRepository.findById(id).orElse(null);
        if (item != null) {
            int newQuantity = item.getQuantity() + quantity;
            if (newQuantity > 5) {
                throw new BusinessException(ErrorCode.CART_ITEM_LIMIT);
            }
            item.setQuantity(newQuantity);
            cartItemRepository.save(item);
        } else {
            CartItem newItem = new CartItem();
            newItem.setCustomerId(customerId);
            newItem.setBatchId(batchId);
            newItem.setQuantity(quantity);
            cartItemRepository.save(newItem);
        }

        return getCart(customerId);
    }

    @Transactional
    public CartResponse updateQuantity(Long customerId, Long batchId, int quantity) {
        if (quantity < 1 || quantity > 5) {
            throw new BusinessException(ErrorCode.CART_ITEM_LIMIT);
        }

        CartItemId id = new CartItemId(customerId, batchId);
        CartItem item = cartItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        item.setQuantity(quantity);
        cartItemRepository.save(item);

        return getCart(customerId);
    }

    @Transactional
    public void removeItem(Long customerId, Long batchId) {
        CartItemId id = new CartItemId(customerId, batchId);
        cartItemRepository.deleteById(id);
    }

    @Transactional
    public void clearCart(Long customerId) {
        List<CartItem> items = cartItemRepository.findByCustomerId(customerId);
        if (!items.isEmpty()) {
            cartItemRepository.deleteAll(items);
        }
    }
}
