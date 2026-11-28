package com.foodrescue.store.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.foodrescue.store.application.port.out.IInternalStoreRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreInternalView;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReadInternalStoreUseCaseTest {
    private final IInternalStoreRepository stores = mock(IInternalStoreRepository.class);
    private final ReadInternalStoreUseCase useCase = new ReadInternalStoreUseCase(stores);

    @Test
    void returnsStoreSummaryForExistingOwner() {
        var expected = new StoreInternalView.OwnerStore(15L, 7L, "APPROVED", 3L);
        when(stores.findByOwnerId(7L)).thenReturn(Optional.of(expected));

        assertEquals(expected, useCase.byOwner(7L));
        verify(stores).findByOwnerId(7L);
    }

    @Test
    void rejectsInvalidOrMissingOwnerLookup() {
        StoreException invalid = assertThrows(StoreException.class, () -> useCase.byOwner(0L));
        assertEquals(400, invalid.status());
        assertEquals("INVALID_OWNER_ID", invalid.code());
        verifyNoInteractions(stores);

        when(stores.findByOwnerId(99L)).thenReturn(Optional.empty());
        StoreException missing = assertThrows(StoreException.class, () -> useCase.byOwner(99L));
        assertEquals(404, missing.status());
        assertEquals("STORE_NOT_FOUND", missing.code());
    }
}
