package com.foodrescue.order.application.usecase;

import com.foodrescue.order.application.port.out.*;
import com.foodrescue.order.domain.model.*;
import com.foodrescue.order.infrastructure.adapter.out.persistence.*;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CreateOrderUseCaseTest {
    private SpringDataOrderRepository orders;
    private SpringDataOrderItemRepository orderItems;
    private SpringDataCartItemRepository cart;
    private IInventoryPort inventory;
    private IStorePolicyPort stores;
    private IOutboxPort outbox;
    private ICheckoutIdempotencyPort idempotency;
    private IReservationRecoveryPort recovery;
    private ReservationRecoveryWorker worker;
    private TransactionTemplate transactions;
    private CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        orders = mock(SpringDataOrderRepository.class);
        orderItems = mock(SpringDataOrderItemRepository.class);
        cart = mock(SpringDataCartItemRepository.class);
        inventory = mock(IInventoryPort.class);
        stores = mock(IStorePolicyPort.class);
        outbox = mock(IOutboxPort.class);
        idempotency = mock(ICheckoutIdempotencyPort.class);
        when(idempotency.claim(anyLong(), anyString())).thenReturn(null);
        recovery = mock(IReservationRecoveryPort.class);
        worker = mock(ReservationRecoveryWorker.class);
        transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        useCase = new CreateOrderUseCase(orders, orderItems, cart, inventory, stores,
                outbox, idempotency, recovery, worker, transactions);

        CartItemEntity item = new CartItemEntity();
        item.setCustomerId(8L);
        item.setBatchId(10L);
        item.setQuantity(2);
        when(cart.findByCustomerId(8L)).thenReturn(List.of(item));
        when(inventory.requireSingleStore(List.of(10L))).thenReturn(1L);
        when(stores.getPolicy(1L)).thenReturn(
                new IStorePolicyPort.StoreCheckoutPolicy(1L, "APPROVED", new BigDecimal("15.00")));
        when(inventory.reserve(any(StockReservationCommand.class), any())).thenAnswer(inv ->
                held(((StockReservationCommand) inv.getArgument(0)).reservationId()));
        when(orders.save(any(OrderEntity.class))).thenAnswer(inv -> {
            OrderEntity order = inv.getArgument(0);
            order.setId(100L);
            return order;
        });
        when(orderItems.save(any(OrderItemEntity.class))).thenAnswer(inv -> {
            OrderItemEntity orderItem = inv.getArgument(0);
            orderItem.setId(200L);
            return orderItem;
        });
    }

    @Test
    void checkoutUsesCartAndMarksReservationOrdered() {
        var response = useCase.createOrder(8L, "checkout-1", UUID.randomUUID());
        assertEquals(100L, response.id());
        assertEquals(new BigDecimal("60000"), response.totalAmount());
        verify(inventory).requireSingleStore(List.of(10L));
        verify(recovery).markOrdered(response.reservationId(), 100L);
        verify(idempotency).complete(8L, "checkout-1", 100L);
    }

    @Test
    void reserveTimeoutFindsHeldReservationBeforeCreatingOrder() {
        when(inventory.reserve(any(), any())).thenThrow(new RuntimeException("timeout"));
        when(inventory.find(any(), any())).thenAnswer(inv -> held(inv.getArgument(0)));
        var response = useCase.createOrder(8L, "checkout-timeout", null);
        assertEquals(100L, response.id());
        verify(inventory).find(eq(response.reservationId()), any());
        verify(inventory, never()).release(any(), any());
    }

    @Test
    void failedPersistenceSchedulesRecovery() {
        when(orders.save(any())).thenThrow(new IllegalStateException("database write failed"));
        assertThrows(IllegalStateException.class,
                () -> useCase.createOrder(8L, "checkout-db-failure", null));
        UUID id = UUID.nameUUIDFromBytes("8:checkout-db-failure".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        verify(recovery).requestRelease(id);
        verify(worker).recoverOne(id);
    }

    @Test
    void failedCommitAlsoSchedulesRecovery() {
        doAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            callback.doInTransaction(mock(TransactionStatus.class));
            throw new IllegalStateException("commit failed");
        }).when(transactions).execute(any());
        assertThrows(IllegalStateException.class,
                () -> useCase.createOrder(8L, "checkout-commit-failure", null));
        UUID id = UUID.nameUUIDFromBytes("8:checkout-commit-failure".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        verify(recovery).requestRelease(id);
        verify(worker).recoverOne(id);
    }

    private StockReservation held(UUID id) {
        return new StockReservation(id, "HELD",
                List.of(new StockReservation.Item(10L, 2, new BigDecimal("30000"))),
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3));
    }
}
