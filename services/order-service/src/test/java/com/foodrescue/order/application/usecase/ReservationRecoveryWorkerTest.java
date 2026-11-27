package com.foodrescue.order.application.usecase;

import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IReservationRecoveryPort;
import com.foodrescue.order.domain.model.StockReservation;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReservationRecoveryWorkerTest {
    private final IReservationRecoveryPort recovery = mock(IReservationRecoveryPort.class);
    private final SpringDataOrderRepository orders = mock(SpringDataOrderRepository.class);
    private final IInventoryPort inventory = mock(IInventoryPort.class);
    private final ReservationRecoveryWorker worker = new ReservationRecoveryWorker(recovery, orders, inventory);

    @Test
    void failedReleaseRemainsPendingForRetry() {
        UUID id = UUID.randomUUID();
        when(recovery.tryClaim(id)).thenReturn(true);
        when(orders.findByReservationId(id)).thenReturn(Optional.empty());
        when(inventory.find(eq(id), any())).thenReturn(new StockReservation(id, "HELD", List.of(), null, null));
        when(inventory.release(eq(id), any())).thenThrow(new IllegalStateException("Catalog offline"));

        worker.recoverOne(id);

        verify(recovery).retryLater(eq(id), contains("Catalog offline"));
        verify(recovery, never()).markReleased(id);
    }

    @Test
    void confirmedReleaseClosesRecovery() {
        UUID id = UUID.randomUUID();
        when(recovery.tryClaim(id)).thenReturn(true);
        when(orders.findByReservationId(id)).thenReturn(Optional.empty());
        when(inventory.find(eq(id), any())).thenReturn(new StockReservation(id, "HELD", List.of(), null, null));
        when(inventory.release(eq(id), any())).thenReturn(new StockReservation(id, "RELEASED", List.of(), null, null));

        worker.recoverOne(id);

        verify(recovery).markReleased(id);
        verify(recovery, never()).retryLater(any(), anyString());
    }
}
