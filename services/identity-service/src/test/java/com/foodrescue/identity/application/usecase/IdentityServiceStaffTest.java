package com.foodrescue.identity.application.usecase;

import com.foodrescue.identity.application.port.out.AuditPort;
import com.foodrescue.identity.application.port.out.PasswordPort;
import com.foodrescue.identity.application.port.out.TokenPort;
import com.foodrescue.identity.application.port.out.UnitOfWork;
import com.foodrescue.identity.domain.exception.IdentityException;
import com.foodrescue.identity.domain.model.UserAccount;
import com.foodrescue.identity.domain.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentityServiceStaffTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AuditPort audit = mock(AuditPort.class);
    private final UnitOfWork transaction = mock(UnitOfWork.class);
    private IdentityService service;

    @BeforeEach
    void setUp() {
        when(transaction.execute(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new IdentityService(users, mock(PasswordPort.class), mock(TokenPort.class), audit, transaction);
        when(users.findById(1L)).thenReturn(Optional.of(account(1L, "STORE_OWNER", null, "ACTIVE", 0)));
        when(users.findById(9L)).thenReturn(Optional.of(account(9L, "STORE_STAFF", 5L, "ACTIVE", 2)));
    }

    @Test
    void lockingStaffInvalidatesExistingTokenAndWritesAudit() {
        UserAccount result = service.setStaffLocked(1L, 5L, 9L, true);

        assertEquals("LOCKED", result.status());
        assertEquals(3, result.tokenVersion());
        verify(users).save(any(UserAccount.class));
        verify(audit).record(1L, "STORE_STAFF_LOCKED", "USER", 9L, "storeId=5");
    }

    @Test
    void refusesStaffFromAnotherStore() {
        IdentityException error = assertThrows(IdentityException.class,
                () -> service.setStaffLocked(1L, 6L, 9L, true));

        assertEquals(404, error.status());
        assertEquals("STAFF_NOT_FOUND", error.code());
        verify(users, never()).save(any());
    }

    @Test
    void repeatedUnlockDoesNotIncrementTokenVersion() {
        UserAccount result = service.setStaffLocked(1L, 5L, 9L, false);

        assertEquals(2, result.tokenVersion());
        verify(users, never()).save(any());
        verifyNoInteractions(audit);
    }

    private UserAccount account(Long id, String role, Long storeId, String status, long version) {
        return new UserAccount(id, "user" + id + "@example.com", "hash", "User " + id, "",
                role, storeId, status, false, LocalDateTime.of(2026, 11, 22, 0, 0), version);
    }
}
