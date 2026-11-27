package com.foodrescue.order.application.usecase;

import com.foodrescue.order.api.dto.AddToCartRequest;
import com.foodrescue.order.api.dto.CartResponse;
import com.foodrescue.order.api.dto.UpdateCartItemRequest;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.domain.exception.OrderException;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataCartItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.CartItemEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.CartItemEntityId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CartUseCaseTest {
    private SpringDataCartItemRepository cartRepository;
    private CartUseCase cartUseCase;

    @BeforeEach
    void setUp() {
        cartRepository = mock(SpringDataCartItemRepository.class);
        cartUseCase = new CartUseCase(cartRepository, mock(IInventoryPort.class));
    }

    @Test
    void getCart_returnsItems() {
        CartItemEntity item = new CartItemEntity();
        item.setCustomerId(8L);
        item.setBatchId(10L);
        item.setQuantity(2);
        item.setAddedAt(LocalDateTime.now());

        when(cartRepository.findByCustomerId(8L)).thenReturn(List.of(item));

        CartResponse response = cartUseCase.getCart(8L);

        assertEquals(8L, response.customerId());
        assertEquals(2, response.totalItems());
        assertEquals(1, response.items().size());
        assertEquals(10L, response.items().get(0).batchId());
    }

    @Test
    void addToCart_newBatch_savesEntity() {
        when(cartRepository.findById(any(CartItemEntityId.class))).thenReturn(Optional.empty());
        when(cartRepository.findByCustomerId(8L)).thenReturn(List.of());

        cartUseCase.addToCart(8L, new AddToCartRequest(10L, 3));

        verify(cartRepository).save(any(CartItemEntity.class));
    }

    @Test
    void addToCart_exceedsFiveBags_throwsValidation() {
        CartItemEntity existing = new CartItemEntity();
        existing.setCustomerId(8L);
        existing.setBatchId(10L);
        existing.setQuantity(4);

        when(cartRepository.findById(any(CartItemEntityId.class))).thenReturn(Optional.of(existing));

        assertThrows(OrderException.class, () -> cartUseCase.addToCart(8L, new AddToCartRequest(10L, 2)));
    }

    @Test
    void updateItem_existingBatch_updatesQuantity() {
        CartItemEntity existing = new CartItemEntity();
        existing.setCustomerId(8L);
        existing.setBatchId(10L);
        existing.setQuantity(2);

        when(cartRepository.findById(any(CartItemEntityId.class))).thenReturn(Optional.of(existing));

        cartUseCase.updateItem(8L, 10L, new UpdateCartItemRequest(4));

        assertEquals(4, existing.getQuantity());
        verify(cartRepository).save(existing);
    }

    @Test
    void removeItem_callsDelete() {
        cartUseCase.removeItem(8L, 10L);
        verify(cartRepository).deleteByCustomerIdAndBatchId(8L, 10L);
    }

    @Test
    void clearCart_callsDeleteByCustomerId() {
        cartUseCase.clearCart(8L);
        verify(cartRepository).deleteByCustomerId(8L);
    }
}
