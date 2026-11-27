package com.foodrescue.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.foodrescue.catalog.application.port.out.IInboxPort;
import com.foodrescue.catalog.application.port.out.IStoreProjectionRepository;
import com.foodrescue.catalog.domain.model.StoreStatusChange;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoreStatusProjectionUseCaseTest {
    private final IInboxPort inbox = mock(IInboxPort.class);
    private final IStoreProjectionRepository projection = mock(IStoreProjectionRepository.class);
    private final StoreStatusProjectionUseCase useCase = new StoreStatusProjectionUseCase(inbox, projection);
    private final UUID eventId = UUID.randomUUID();
    private final StoreStatusChange change = new StoreStatusChange(15L, 7L, "APPROVED", 2L);

    @Test
    void appliesNewEventOnce() {
        when(inbox.markProcessed(StoreStatusProjectionUseCase.CONSUMER_NAME, eventId)).thenReturn(true);

        useCase.apply(eventId, change);

        verify(projection).applyIfNewer(change);
    }

    @Test
    void duplicateEventIsIgnored() {
        when(inbox.markProcessed(StoreStatusProjectionUseCase.CONSUMER_NAME, eventId)).thenReturn(false);

        useCase.apply(eventId, change);

        verifyNoInteractions(projection);
    }

    @Test
    void rejectsUnknownStatusAndInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> new StoreStatusChange(15L, 7L, "PENDING", 1L));
        assertThrows(IllegalArgumentException.class, () -> new StoreStatusChange(0L, 7L, "APPROVED", 1L));
        assertThrows(IllegalArgumentException.class, () -> new StoreStatusChange(15L, 7L, "APPROVED", 0L));
    }
}
