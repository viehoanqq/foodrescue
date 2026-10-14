package com.foodrescue.cart;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.foodrescue.common.entity.CartItem;
import com.foodrescue.common.entity.CartItemId;
import com.foodrescue.common.entity.RescueBatch;
import com.foodrescue.common.entity.Store;
import com.foodrescue.common.enums.BatchStatus;
import com.foodrescue.common.exception.BusinessException;
import com.foodrescue.common.exception.ErrorCode;
import com.foodrescue.common.repository.CartItemRepository;
import com.foodrescue.common.repository.RescueBatchRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartItemRepository cartItemRepository;
    @Mock RescueBatchRepository rescueBatchRepository;

    CartService cartService;

    Store store1;
    Store store2;
    RescueBatch batch1;
    RescueBatch batch2;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartItemRepository, rescueBatchRepository);

        store1 = new Store();
        store1.setId(1L);
        store1.setName("An Phát");

        store2 = new Store();
        store2.setId(2L);
        store2.setName("Sweet Paris");

        LocalDateTime now = LocalDateTime.now();

        batch1 = new RescueBatch();
        batch1.setId(1L);
        batch1.setTitle("Bánh mì 1");
        batch1.setRescuePrice(new BigDecimal("30000"));
        batch1.setOriginalPrice(new BigDecimal("60000"));
        batch1.setRemainingQuantity(5);
        batch1.setStatus(BatchStatus.AVAILABLE);
        batch1.setPickupStart(now.plusHours(1));
        batch1.setPickupEnd(now.plusHours(2));
        batch1.setStore(store1);

        batch2 = new RescueBatch();
        batch2.setId(2L);
        batch2.setTitle("Bánh ngọt 2");
        batch2.setRescuePrice(new BigDecimal("40000"));
        batch2.setOriginalPrice(new BigDecimal("80000"));
        batch2.setRemainingQuantity(5);
        batch2.setStatus(BatchStatus.AVAILABLE);
        batch2.setPickupStart(now.plusHours(1));
        batch2.setPickupEnd(now.plusHours(2));
        batch2.setStore(store2);
    }

    @Test
    void themVaoGio_thanhCong() {
        when(rescueBatchRepository.findById(1L)).thenReturn(Optional.of(batch1));
        when(cartItemRepository.findByCustomerId(8L)).thenReturn(List.of());
        when(cartItemRepository.findById(new CartItemId(8L, 1L))).thenReturn(Optional.empty());

        CartResponse res = cartService.addToCart(8L, 1L, 2);

        verify(cartItemRepository).save(any(CartItem.class));
        assertNotNull(res);
    }

    @Test
    void themVaoGio_khacCuaHang_nemLoiCartOtherStore() {
        CartItem existing = new CartItem();
        existing.setCustomerId(8L);
        existing.setBatchId(1L);
        existing.setQuantity(1);

        when(rescueBatchRepository.findById(2L)).thenReturn(Optional.of(batch2));
        when(cartItemRepository.findByCustomerId(8L)).thenReturn(List.of(existing));
        when(rescueBatchRepository.findById(1L)).thenReturn(Optional.of(batch1));

        BusinessException ex = assertThrows(BusinessException.class, () -> cartService.addToCart(8L, 2L, 1));
        assertEquals(ErrorCode.CART_OTHER_STORE, ex.getCode());
    }

    @Test
    void themVaoGio_vuotQua5Cai_nemLoiCartItemLimit() {
        CartItem existing = new CartItem();
        existing.setCustomerId(8L);
        existing.setBatchId(1L);
        existing.setQuantity(4);

        when(rescueBatchRepository.findById(1L)).thenReturn(Optional.of(batch1));
        when(cartItemRepository.findByCustomerId(8L)).thenReturn(List.of(existing));
        when(cartItemRepository.findById(new CartItemId(8L, 1L))).thenReturn(Optional.of(existing));

        BusinessException ex = assertThrows(BusinessException.class, () -> cartService.addToCart(8L, 1L, 2));
        assertEquals(ErrorCode.CART_ITEM_LIMIT, ex.getCode());
    }

    @Test
    void xoaSachGio_thanhCong() {
        CartItem ci = new CartItem();
        ci.setCustomerId(8L);
        ci.setBatchId(1L);

        when(cartItemRepository.findByCustomerId(8L)).thenReturn(List.of(ci));

        cartService.clearCart(8L);

        verify(cartItemRepository).deleteAll(List.of(ci));
    }
}
