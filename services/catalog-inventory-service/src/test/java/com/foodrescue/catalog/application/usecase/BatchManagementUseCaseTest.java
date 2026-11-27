package com.foodrescue.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.foodrescue.catalog.application.port.out.IBatchManagementRepository;
import com.foodrescue.catalog.application.port.out.IOutboxPort;
import com.foodrescue.catalog.application.port.out.IStoreOwnerLookupPort;
import com.foodrescue.catalog.application.port.out.IStoreProjectionRepository;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.domain.model.Actor;
import com.foodrescue.catalog.domain.model.CatalogStore;
import com.foodrescue.catalog.domain.model.RescueBatch;
import com.foodrescue.catalog.domain.model.StockMovement;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchManagementUseCaseTest {
    private final IBatchManagementRepository batches = mock(IBatchManagementRepository.class);
    private final IStoreProjectionRepository projections = mock(IStoreProjectionRepository.class);
    private final IStoreOwnerLookupPort ownerLookup = mock(IStoreOwnerLookupPort.class);
    private final com.foodrescue.catalog.application.port.out.IStoreStatusPort storeStatus =
            mock(com.foodrescue.catalog.application.port.out.IStoreStatusPort.class);
    private final IOutboxPort outbox = mock(IOutboxPort.class);
    private final BatchManagementUseCase useCase = new BatchManagementUseCase(
            batches, new CatalogStoreAccess(projections, ownerLookup, storeStatus), outbox);
    private final Actor staff = new Actor(30L, "STORE_STAFF", 15L);
    private final RescueBatch batch = new RescueBatch(5L, 15L, 2L, "Túi", null, null,
            BigDecimal.valueOf(100_000), BigDecimal.valueOf(50_000), 5, 2,
            LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2),
            "AVAILABLE", LocalDateTime.now(), 3L, false);

    private void approvedStore() {
        CatalogStore approved = new CatalogStore(15L, 7L, "APPROVED", 2L);
        when(projections.findByStoreId(15L)).thenReturn(Optional.of(approved));
        when(storeStatus.findByStoreId(15L)).thenReturn(Optional.of(approved));
    }

    @Test
    void staffRemovalWritesNegativeAppendOnlyMovementAndAudit() {
        approvedStore();
        when(batches.findForUpdate(15L, 5L)).thenReturn(Optional.of(batch));
        when(batches.save(any())).thenAnswer(call -> call.getArgument(0));

        RescueBatch changed = useCase.adjustStock(staff, 5L, 1, "REMOVED", "Hàng hỏng");

        assertEquals(1, changed.remainingQuantity());
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(batches).appendMovement(movement.capture());
        assertEquals(-1, movement.getValue().changeQty());
        assertEquals("REMOVED", movement.getValue().reason());
        verify(outbox).append(any());
    }

    @Test
    void staffCannotRemoveMoreThanRemainingStock() {
        approvedStore();
        when(batches.findForUpdate(15L, 5L)).thenReturn(Optional.of(batch));

        CatalogException error = assertThrows(CatalogException.class,
                () -> useCase.adjustStock(staff, 5L, 3, "REMOVED", null));

        assertEquals("STOCK_NOT_ENOUGH", error.code());
        verify(batches, never()).save(any());
        verify(batches, never()).appendMovement(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void cancellingBatchWithHeldReservationIsRejected() {
        approvedStore();
        when(batches.findForUpdate(15L, 5L)).thenReturn(Optional.of(batch));
        when(batches.hasHeldReservations(5L)).thenReturn(true);

        CatalogException error = assertThrows(CatalogException.class,
                () -> useCase.cancel(staff, 5L, "Không còn hàng", false));

        assertEquals("BATCH_HAS_ACTIVE_RESERVATION", error.code());
        verify(batches, never()).save(any());
        verify(batches, never()).appendMovement(any());
        verifyNoInteractions(outbox);
    }
}
