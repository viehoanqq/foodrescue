package com.foodrescue.order.application.usecase;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.domain.exception.OrderException;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataPendingReleaseRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CustomerOrderUseCaseTest {
    private SpringDataOrderRepository orderRepository;
    private SpringDataOrderItemRepository orderItemRepository;
    private SpringDataPendingReleaseRepository pendingReleaseRepository;
    private IInventoryPort inventoryPort;
    private IOutboxPort outboxPort;
    private InventoryReleaseService inventoryReleaseService;
    private OrderSupport orderSupport;

    private CustomerOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        orderRepository = mock(SpringDataOrderRepository.class);
        orderItemRepository = mock(SpringDataOrderItemRepository.class);
        pendingReleaseRepository = mock(SpringDataPendingReleaseRepository.class);
        inventoryPort = mock(IInventoryPort.class);
        outboxPort = mock(IOutboxPort.class);

        inventoryReleaseService = new InventoryReleaseService(inventoryPort, pendingReleaseRepository);
        orderSupport = new OrderSupport(orderItemRepository, outboxPort);

        useCase = new CustomerOrderUseCase(
                orderRepository,
                orderItemRepository,
                inventoryReleaseService,
                outboxPort,
                orderSupport);
    }

    @Test
    void cancelOrder_pendingPayment_releasesReservationAndEmitsStateChanged() {
        Long customerId = 8L;
        Long orderId = 100L;
        UUID reservationId = UUID.randomUUID();

        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setStoreId(1L);
        order.setStatus("PENDING_PAYMENT");
        order.setPaymentStatus("UNPAID");
        order.setPickupCode("ABC123");
        order.setTotalAmount(new BigDecimal("50000"));
        order.setCommissionAmount(new BigDecimal("7500"));
        order.setStoreEarning(new BigDecimal("42500"));
        order.setReservationId(reservationId);
        order.setAggregateVersion(1L);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

        OrderResponse res = useCase.cancelOrder(customerId, orderId, "Không muốn mua nữa");

        assertEquals("CANCELLED", res.status());
        assertEquals("UNPAID", res.paymentStatus());
        assertEquals(2L, res.aggregateVersion());
        assertNull(res.pickupCode());

        verify(inventoryPort).release(eq(reservationId), any());
        verify(outboxPort, times(2)).append(any(EventEnvelope.class));
    }

    @Test
    void cancelOrder_paid_emitsRefundRequiredOnce() {
        Long customerId = 8L;
        Long orderId = 100L;
        UUID reservationId = UUID.randomUUID();

        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setStoreId(1L);
        order.setStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setPickupCode("ABC123");
        order.setTotalAmount(new BigDecimal("50000"));
        order.setCommissionAmount(new BigDecimal("7500"));
        order.setStoreEarning(new BigDecimal("42500"));
        order.setReservationId(reservationId);
        order.setAggregateVersion(2L);

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.claimRefundRequest(orderId)).thenReturn(1);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

        OrderResponse res = useCase.cancelOrder(customerId, orderId, "Đổi ý");

        assertEquals("CANCELLED", res.status());
        assertEquals("REFUND_PENDING", res.paymentStatus());
        assertEquals(3L, res.aggregateVersion());

        // Should append order.state-changed.v1, order.refund-required.v1, audit.action.v1
        verify(outboxPort, times(3)).append(any(EventEnvelope.class));
    }

    @Test
    void cancelOrder_wrongCustomer_throwsForbidden() {
        OrderEntity order = new OrderEntity();
        order.setId(100L);
        order.setCustomerId(99L);

        when(orderRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(order));

        assertThrows(OrderException.class, () -> useCase.cancelOrder(8L, 100L, "test"));
    }

    @Test
    void getOrderById_pickupCodeOnlyVisibleAfterPaymentSuccess() {
        OrderEntity order = new OrderEntity();
        order.setId(101L);
        order.setCustomerId(8L);
        order.setStoreId(1L);
        order.setStatus("CONFIRMED");
        order.setPaymentStatus("PAID");
        order.setPickupCode("XYZ999");

        when(orderRepository.findById(101L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(101L)).thenReturn(List.of());

        OrderResponse paidRes = useCase.getOrderById(8L, 101L);
        assertEquals("XYZ999", paidRes.pickupCode());

        order.setStatus("PENDING_PAYMENT");
        order.setPaymentStatus("UNPAID");
        OrderResponse unpaidRes = useCase.getOrderById(8L, 101L);
        assertNull(unpaidRes.pickupCode());
    }
}
