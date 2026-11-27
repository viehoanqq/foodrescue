package com.foodrescue.order.application.usecase;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.order.api.dto.CreateOrderItemRequest;
import com.foodrescue.order.api.dto.CreateOrderRequest;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.application.port.out.IStorePolicyPort;
import com.foodrescue.order.domain.exception.OrderException;
import com.foodrescue.order.domain.model.StockReservation;
import com.foodrescue.order.domain.model.StockReservationCommand;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataCartItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CreateOrderUseCaseTest {
    private SpringDataOrderRepository orderRepository;
    private SpringDataOrderItemRepository orderItemRepository;
    private SpringDataCartItemRepository cartRepository;
    private IInventoryPort inventoryPort;
    private IStorePolicyPort storePolicyPort;
    private IOutboxPort outboxPort;

    private CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        orderRepository = mock(SpringDataOrderRepository.class);
        orderItemRepository = mock(SpringDataOrderItemRepository.class);
        cartRepository = mock(SpringDataCartItemRepository.class);
        inventoryPort = mock(IInventoryPort.class);
        storePolicyPort = mock(IStorePolicyPort.class);
        outboxPort = mock(IOutboxPort.class);

        useCase = new CreateOrderUseCase(
                orderRepository,
                orderItemRepository,
                cartRepository,
                inventoryPort,
                storePolicyPort,
                outboxPort);
    }

    @Test
    void createOrder_success_emitsOutboxEvent() {
        Long customerId = 8L;
        Long storeId = 1L;
        UUID reservationId = UUID.randomUUID();

        when(storePolicyPort.getPolicy(storeId))
                .thenReturn(new IStorePolicyPort.StoreCheckoutPolicy(storeId, "APPROVED", new BigDecimal("15.00")));

        StockReservation reservation = new StockReservation(
                reservationId,
                "HELD",
                List.of(new StockReservation.Item(10L, 2, new BigDecimal("30000"))),
                LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusHours(3));
        when(inventoryPort.reserve(any(StockReservationCommand.class), any())).thenReturn(reservation);

        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });

        when(orderItemRepository.save(any(OrderItemEntity.class))).thenAnswer(inv -> {
            OrderItemEntity oi = inv.getArgument(0);
            oi.setId(200L);
            return oi;
        });

        CreateOrderRequest req = new CreateOrderRequest(storeId, List.of(new CreateOrderItemRequest(10L, 2)));
        OrderResponse response = useCase.createOrder(customerId, req, UUID.randomUUID());

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("PENDING_PAYMENT", response.status());
        assertEquals(new BigDecimal("60000"), response.totalAmount());
        assertEquals(new BigDecimal("9000"), response.commissionAmount());
        assertEquals(new BigDecimal("51000"), response.storeEarning());

        verify(orderRepository).save(any(OrderEntity.class));
        verify(orderItemRepository).save(any(OrderItemEntity.class));
        verify(outboxPort).append(any(EventEnvelope.class));
        verify(cartRepository).deleteByCustomerIdAndBatchId(customerId, 10L);
    }

    @Test
    void createOrder_storeNotApproved_throwsConflict() {
        Long customerId = 8L;
        Long storeId = 2L;

        when(storePolicyPort.getPolicy(storeId))
                .thenReturn(new IStorePolicyPort.StoreCheckoutPolicy(storeId, "PENDING", new BigDecimal("15.00")));

        CreateOrderRequest req = new CreateOrderRequest(storeId, List.of(new CreateOrderItemRequest(10L, 1)));

        OrderException ex = assertThrows(OrderException.class, () -> useCase.createOrder(customerId, req, null));
        assertEquals("STORE_NOT_APPROVED", ex.code());
        verifyNoInteractions(inventoryPort);
    }

    @Test
    void createOrder_cartEmpty_throwsCartEmpty() {
        when(cartRepository.findByCustomerId(8L)).thenReturn(List.of());

        OrderException ex = assertThrows(OrderException.class, () -> useCase.createOrder(8L, null, null));
        assertEquals("CART_EMPTY", ex.code());
    }
}
