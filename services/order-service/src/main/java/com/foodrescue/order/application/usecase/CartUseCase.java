package com.foodrescue.order.application.usecase;

import com.foodrescue.order.api.dto.AddToCartRequest;
import com.foodrescue.order.api.dto.CartItemDto;
import com.foodrescue.order.api.dto.CartResponse;
import com.foodrescue.order.api.dto.UpdateCartItemRequest;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.domain.exception.OrderException;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataCartItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.CartItemEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.CartItemEntityId;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartUseCase {
    private final SpringDataCartItemRepository cartRepository;
    private final IInventoryPort inventoryPort;

    public CartUseCase(SpringDataCartItemRepository cartRepository, IInventoryPort inventoryPort) {
        this.cartRepository = cartRepository;
        this.inventoryPort = inventoryPort;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long customerId) {
        List<CartItemEntity> entities = cartRepository.findByCustomerId(customerId);
        List<CartItemDto> dtos = entities.stream()
                .map(e -> new CartItemDto(e.getBatchId(), e.getQuantity(), e.getAddedAt()))
                .toList();
        int total = dtos.stream().mapToInt(CartItemDto::quantity).sum();
        return new CartResponse(customerId, total, dtos);
    }

    @Transactional
    public CartResponse addToCart(Long customerId, AddToCartRequest req) {
        List<Long> batchIds = cartRepository.findByCustomerId(customerId).stream()
                .map(CartItemEntity::getBatchId).distinct().toList();
        if (!batchIds.contains(req.batchId())) {
            var allBatchIds = new java.util.ArrayList<>(batchIds);
            allBatchIds.add(req.batchId());
            try {
                inventoryPort.requireSingleStore(allBatchIds);
            } catch (com.foodrescue.buildingblocks.http.InternalServiceException ex) {
                if (ex.status() == 409) {
                    throw OrderException.conflict("MULTI_STORE_CART", "Cart can contain batches from one store only");
                }
                throw ex;
            }
        }
        CartItemEntityId id = new CartItemEntityId(customerId, req.batchId());
        CartItemEntity entity = cartRepository.findById(id).orElse(null);

        int newQty = req.quantity();
        if (entity != null) {
            newQty += entity.getQuantity();
            if (newQty > 5) {
                throw OrderException.validation("Số lượng trong giỏ không được vượt quá 5 phần");
            }
            entity.setQuantity(newQty);
        } else {
            entity = new CartItemEntity();
            entity.setCustomerId(customerId);
            entity.setBatchId(req.batchId());
            entity.setQuantity(newQty);
            entity.setAddedAt(LocalDateTime.now());
        }
        cartRepository.save(entity);
        return getCart(customerId);
    }

    @Transactional
    public CartResponse updateItem(Long customerId, Long batchId, UpdateCartItemRequest req) {
        CartItemEntityId id = new CartItemEntityId(customerId, batchId);
        CartItemEntity entity = cartRepository.findById(id)
                .orElseThrow(() -> OrderException.notFound("Không tìm thấy sản phẩm trong giỏ"));

        entity.setQuantity(req.quantity());
        cartRepository.save(entity);
        return getCart(customerId);
    }

    @Transactional
    public CartResponse removeItem(Long customerId, Long batchId) {
        cartRepository.deleteByCustomerIdAndBatchId(customerId, batchId);
        return getCart(customerId);
    }

    @Transactional
    public void clearCart(Long customerId) {
        cartRepository.deleteByCustomerId(customerId);
    }
}
