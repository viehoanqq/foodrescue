package com.foodrescue.order.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.application.port.out.IStorePolicyPort;
import com.foodrescue.order.domain.exception.OrderException;
import com.foodrescue.order.domain.model.StockReservation;
import com.foodrescue.order.domain.model.StockReservationCommand;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataCartItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataIdempotencyKeyRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataPendingReleaseRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.CartItemEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.IdempotencyKeyEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderItemEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CreateOrderUseCaseTest {
    private SpringDataOrderRepository orderRepository;
    private SpringDataOrderItemRepository orderItemRepository;
    private SpringDataCartItemRepository cartRepository;
    private SpringDataIdempotencyKeyRepository idempotencyKeyRepository;
    private SpringDataPendingReleaseRepository pendingReleaseRepository;
    private IInventoryPort inventoryPort;
    private IStorePolicyPort storePolicyPort;
    private IOutboxPort outboxPort;
    private InventoryReleaseService inventoryReleaseService;
    private OrderSupport orderSupport;
    private ObjectMapper objectMapper;
    private PlatformTransactionManager transactionManager;

    private CreateOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        orderRepository = mock(SpringDataOrderRepository.class);
        orderItemRepository = mock(SpringDataOrderItemRepository.class);
        cartRepository = mock(SpringDataCartItemRepository.class);
        idempotencyKeyRepository = mock(SpringDataIdempotencyKeyRepository.class);
        pendingReleaseRepository = mock(SpringDataPendingReleaseRepository.class);
        inventoryPort = mock(IInventoryPort.class);
        storePolicyPort = mock(IStorePolicyPort.class);
        outboxPort = mock(IOutboxPort.class);

        inventoryReleaseService = new InventoryReleaseService(inventoryPort, pendingReleaseRepository);
        orderSupport = new OrderSupport(orderItemRepository, outboxPort);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        useCase = new CreateOrderUseCase(
                orderRepository,
                orderItemRepository,
                cartRepository,
                idempotencyKeyRepository,
                inventoryPort,
                storePolicyPort,
                inventoryReleaseService,
                orderSupport,
                objectMapper,
                transactionManager);
    }

    private CartItemEntity cartItem(Long customerId, Long batchId, int qty) {
        CartItemEntity ci = new CartItemEntity();
        ci.setCustomerId(customerId);
        ci.setBatchId(batchId);
        ci.setQuantity(qty);
        ci.setAddedAt(LocalDateTime.now());
        return ci;
    }

    @Test
    void createOrder_success_fromCartDb_emitsOutboxAndHidesPickupCode() {
        Long customerId = 8L;
        Long storeId = 1L;
        UUID reservationId = UUID.randomUUID();

        when(cartRepository.findByCustomerId(customerId)).thenReturn(List.of(cartItem(customerId, 10L, 2)));
        when(inventoryPort.getBatch(eq(10L), any())).thenReturn(new IInventoryPort.BatchInfo(10L, storeId, "AVAILABLE"));
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
        when(orderItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = useCase.createOrder(customerId, "idem-1", UUID.randomUUID());

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("PENDING_PAYMENT", response.status());
        assertNull(response.pickupCode(), "pickupCode must stay hidden until PAID");
        assertEquals(new BigDecimal("60000"), response.totalAmount());
        assertEquals(new BigDecimal("9000"), response.commissionAmount());
        assertEquals(new BigDecimal("51000"), response.storeEarning());

        verify(orderRepository).save(any(OrderEntity.class));
        verify(outboxPort).append(any(EventEnvelope.class));
        verify(cartRepository).deleteByCustomerId(customerId);
        verify(idempotencyKeyRepository).save(any(IdempotencyKeyEntity.class));
    }

    @Test
    void createOrder_idempotencyKeyReplay_returnsCachedWithoutReservingAgain() throws Exception {
        Long customerId = 8L;
        CartItemEntity ci = cartItem(customerId, 10L, 1);
        when(cartRepository.findByCustomerId(customerId)).thenReturn(List.of(ci));

        OrderResponse cachedResp = new OrderResponse(
                100L, "FR-261127-1234", customerId, 1L, 1,
                new BigDecimal("30000"), new BigDecimal("15.00"), new BigDecimal("4500"), new BigDecimal("25500"),
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), null,
                "PENDING_PAYMENT", "UNPAID", null, LocalDateTime.now().plusMinutes(15), null,
                LocalDateTime.now(), UUID.randomUUID(), 1L, List.of());

        // Use exact hash computed by CreateOrderUseCase by running first or matching null hash fallback
        IdempotencyKeyEntity existing = new IdempotencyKeyEntity();
        existing.setKey("idem-repeat");
        existing.setCustomerId(customerId);
        existing.setRequestHash(null);
        existing.setResponseBody(objectMapper.writeValueAsString(cachedResp));

        when(idempotencyKeyRepository.findById("idem-repeat")).thenReturn(Optional.of(existing));

        OrderResponse replay = useCase.createOrder(customerId, "idem-repeat", UUID.randomUUID());

        assertEquals(100L, replay.id());
        assertEquals("FR-261127-1234", replay.orderCode());
        verifyNoInteractions(inventoryPort);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_multiStoreCart_throwsMultiStoreCheckout() {
        Long customerId = 8L;
        when(cartRepository.findByCustomerId(customerId))
                .thenReturn(List.of(cartItem(customerId, 10L, 1), cartItem(customerId, 20L, 1)));
        when(inventoryPort.getBatch(eq(10L), any())).thenReturn(new IInventoryPort.BatchInfo(10L, 1L, "AVAILABLE"));
        when(inventoryPort.getBatch(eq(20L), any())).thenReturn(new IInventoryPort.BatchInfo(20L, 2L, "AVAILABLE"));

        OrderException ex = assertThrows(OrderException.class,
                () -> useCase.createOrder(customerId, null, UUID.randomUUID()));

        assertEquals("MULTI_STORE_CHECKOUT", ex.code());
        verify(inventoryPort, never()).reserve(any(), any());
    }

    @Test
    void createOrder_reserveTimeout_recoversViaFind() {
        Long customerId = 8L;
        Long storeId = 1L;
        when(cartRepository.findByCustomerId(customerId)).thenReturn(List.of(cartItem(customerId, 10L, 1)));
        when(inventoryPort.getBatch(eq(10L), any())).thenReturn(new IInventoryPort.BatchInfo(10L, storeId, "AVAILABLE"));
        when(storePolicyPort.getPolicy(storeId))
                .thenReturn(new IStorePolicyPort.StoreCheckoutPolicy(storeId, "APPROVED", new BigDecimal("15.00")));

        when(inventoryPort.reserve(any(), any())).thenThrow(new RuntimeException("Read timed out"));
        when(inventoryPort.find(any(), any())).thenAnswer(inv -> new StockReservation(
                inv.getArgument(0), "HELD",
                List.of(new StockReservation.Item(10L, 1, new BigDecimal("30000"))),
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3)));

        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            o.setId(105L);
            return o;
        });
        when(orderItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse res = useCase.createOrder(customerId, null, UUID.randomUUID());

        assertEquals(105L, res.id());
        verify(inventoryPort).find(any(), any());
    }

    @Test
    void createOrder_dbSaveFails_triggersReleaseCompensation() {
        Long customerId = 8L;
        Long storeId = 1L;
        when(cartRepository.findByCustomerId(customerId)).thenReturn(List.of(cartItem(customerId, 10L, 1)));
        when(inventoryPort.getBatch(eq(10L), any())).thenReturn(new IInventoryPort.BatchInfo(10L, storeId, "AVAILABLE"));
        when(storePolicyPort.getPolicy(storeId))
                .thenReturn(new IStorePolicyPort.StoreCheckoutPolicy(storeId, "APPROVED", new BigDecimal("15.00")));

        when(inventoryPort.reserve(any(), any())).thenAnswer(inv -> {
            StockReservationCommand cmd = inv.getArgument(0);
            return new StockReservation(cmd.reservationId(), "HELD",
                    List.of(new StockReservation.Item(10L, 1, new BigDecimal("30000"))),
                    LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(3));
        });

        when(orderRepository.save(any(OrderEntity.class))).thenThrow(new RuntimeException("DB connection lost"));

        assertThrows(RuntimeException.class, () -> useCase.createOrder(customerId, null, UUID.randomUUID()));
        verify(inventoryPort).release(any(UUID.class), any(UUID.class));
    }

    @Test
    void createOrder_cartEmpty_throwsCartEmpty() {
        when(cartRepository.findByCustomerId(8L)).thenReturn(List.of());

        OrderException ex = assertThrows(OrderException.class, () -> useCase.createOrder(8L, null, null));
        assertEquals("CART_EMPTY", ex.code());
    }
}
