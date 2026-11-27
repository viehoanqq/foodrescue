package com.foodrescue.order.application.usecase;

import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderSchedulerUseCaseTest {
    private final SpringDataOrderRepository orders = mock(SpringDataOrderRepository.class);
    private final SpringDataOrderItemRepository items = mock(SpringDataOrderItemRepository.class);
    private final IInventoryPort inventory = mock(IInventoryPort.class);
    private final IOutboxPort outbox = mock(IOutboxPort.class);
    private final OrderSchedulerUseCase scheduler = new OrderSchedulerUseCase(orders, items, inventory, outbox);

    @Test
    void paymentConfirmedAfterScanPreventsExpiry() {
        OrderEntity current = new OrderEntity();
        current.setId(10L);
        current.setStatus("CONFIRMED");
        current.setPaymentExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(orders.findExpiredPendingOrderIds(eq("PENDING_PAYMENT"), any())).thenReturn(List.of(10L));
        when(orders.findLockedById(10L)).thenReturn(Optional.of(current));

        scheduler.expireUnpaidOrders();

        verify(orders, never()).save(any());
        verifyNoInteractions(inventory, outbox);
    }

    @Test
    void handoverAfterScanPreventsNoShow() {
        OrderEntity current = new OrderEntity();
        current.setId(11L);
        current.setStatus("COMPLETED");
        current.setPickupEnd(LocalDateTime.now().minusHours(1));
        when(orders.findNoShowOrderIds(eq("CONFIRMED"), any())).thenReturn(List.of(11L));
        when(orders.findLockedById(11L)).thenReturn(Optional.of(current));

        scheduler.markNoShowOrders();

        verify(orders, never()).save(any());
        verifyNoInteractions(outbox);
    }
}
