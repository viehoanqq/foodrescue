package com.foodrescue.store.application.usecase;

import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.application.port.out.IStaffIdentityPort;
import com.foodrescue.store.application.port.out.IStaffIdentityPort.CreatedStaff;
import com.foodrescue.store.application.port.out.IStaffIdentityPort.NewStaff;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.OwnedStore;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoreStaffUseCaseTest {
    private final IStoreRepository stores = mock(IStoreRepository.class);
    private final IStaffIdentityPort identity = mock(IStaffIdentityPort.class);
    private final StoreStaffUseCase useCase = new StoreStaffUseCase(stores, identity);
    private final NewStaff staff = new NewStaff("staff@example.com", "Nhân viên", "0123456789");

    @Test
    void usesStoreOwnedByAuthenticatedUser() {
        when(stores.findByOwnerId(7L)).thenReturn(Optional.of(new OwnedStore(15L, 7L, "APPROVED")));
        CreatedStaff created = new CreatedStaff(30L, staff.email(), staff.fullName(), staff.phone(),
                "STORE_STAFF", 15L, "ACTIVE", true, "temporary-password");
        when(identity.create(7L, 15L, staff)).thenReturn(created);

        assertEquals(created, useCase.execute(7L, staff));
        verify(identity).create(7L, 15L, staff);
    }

    @Test
    void pendingStoreCannotCreateStaff() {
        when(stores.findByOwnerId(7L)).thenReturn(Optional.of(new OwnedStore(15L, 7L, "PENDING")));

        StoreException error = assertThrows(StoreException.class, () -> useCase.execute(7L, staff));

        assertEquals("STORE_NOT_APPROVED", error.code());
        verifyNoInteractions(identity);
    }

    @Test
    void mismatchedOwnerCannotCreateStaff() {
        when(stores.findByOwnerId(7L)).thenReturn(Optional.of(new OwnedStore(15L, 8L, "APPROVED")));

        StoreException error = assertThrows(StoreException.class, () -> useCase.execute(7L, staff));

        assertEquals("STORE_OWNER_MISMATCH", error.code());
        verifyNoInteractions(identity);
    }
}
