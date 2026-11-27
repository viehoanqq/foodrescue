package com.foodrescue.store.application.usecase;

import com.foodrescue.store.application.port.out.IAdminStoreRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreView;
import com.foodrescue.store.domain.model.enums.StoreStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadAdminStoreUseCaseTest {
    @Test
    void listParsesValidCaseInsensitiveStatusAndMapsInvalidTo400() {
        IAdminStoreRepository repo = mock(IAdminStoreRepository.class);
        ReadAdminStoreUseCase useCase = new ReadAdminStoreUseCase(repo);
        StoreView.AdminPage empty = new StoreView.AdminPage(List.of(), 0, 20, 0, 0);
        when(repo.findForAdmin(StoreStatus.APPROVED, 0, 20)).thenReturn(empty);

        assertSame(empty, useCase.list(" approved ", 0, 20));
        verify(repo).findForAdmin(StoreStatus.APPROVED, 0, 20);

        StoreException invalid = assertThrows(StoreException.class, () -> useCase.list("UNKNOWN", 0, 20));
        assertEquals(400, invalid.status());
        assertEquals("INVALID_STATUS", invalid.code());
    }
}
