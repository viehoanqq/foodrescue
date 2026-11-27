package com.foodrescue.identity.application.usecase;

import com.foodrescue.identity.application.port.out.IAuditRepository;
import com.foodrescue.identity.application.port.out.IPasswordPort;
import com.foodrescue.identity.application.port.out.ITokenPort;
import com.foodrescue.identity.application.port.out.IUnitOfWork;
import com.foodrescue.identity.domain.exception.IdentityException;
import com.foodrescue.identity.domain.model.UserAccount;
import com.foodrescue.identity.domain.model.enums.UserRole;
import com.foodrescue.identity.domain.model.enums.UserStatus;
import com.foodrescue.identity.domain.repository.IUserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentityServiceStaffTest {
    private final IUserRepository users = mock(IUserRepository.class);
    private final IAuditRepository audit = mock(IAuditRepository.class);
    private final IUnitOfWork transaction = mock(IUnitOfWork.class);
    private IdentityService service;

    @BeforeEach
    void setUp() {
        when(transaction.execute(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new IdentityService(users, mock(IPasswordPort.class), mock(ITokenPort.class), audit, transaction,
                mock(com.foodrescue.identity.application.port.out.IGoogleIdentityPort.class),
                mock(com.foodrescue.identity.application.port.out.IEmailOtpPort.class));
        when(users.findById(1L)).thenReturn(Optional.of(account(1L, UserRole.STORE_OWNER, null, UserStatus.ACTIVE, 0)));
        when(users.findById(9L)).thenReturn(Optional.of(account(9L, UserRole.STORE_STAFF, 5L, UserStatus.ACTIVE, 2)));
    }

    @Test
    void lockingStaffInvalidatesExistingTokenAndWritesAudit() {
        UserAccount result = service.setStaffLocked(1L, 5L, 9L, true);

        assertEquals(UserStatus.LOCKED, result.status());
        assertEquals(3, result.tokenVersion());
        verify(users).save(any(UserAccount.class));
        verify(audit).record(1L, "STORE_STAFF_LOCKED", "USER", 9L, "storeId=5");
    }

    @Test
    void creatingStaffWritesOneAuditRowForTheOwner() {
        when(users.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(invocation -> {
            UserAccount draft = invocation.getArgument(0);
            return new UserAccount(20L, draft.email(), draft.passwordHash(), draft.fullName(), draft.phone(),
                    draft.role(), draft.storeId(), draft.status(), draft.mustChangePassword(),
                    draft.createdAt(), draft.tokenVersion());
        });

        IdentityService.StaffResult result = service.createStaff(1L, 5L, "new@example.com", "New Staff", "0123456789");

        assertEquals(20L, result.user().id());
        verify(audit).record(1L, "STORE_STAFF_CREATED", "USER", 20L, "storeId=5");
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

    @Test
    void searchMapsInvalidRoleOrStatusTo400ValidationError() {
        IdentityException badRole = assertThrows(IdentityException.class,
                () -> service.search("UNKNOWN_ROLE", null, null, 0, 20));
        assertEquals(400, badRole.status());
        assertEquals("VALIDATION_ERROR", badRole.code());

        IdentityException badStatus = assertThrows(IdentityException.class,
                () -> service.search("customer", "BANNED", null, 0, 20));
        assertEquals(400, badStatus.status());
        assertEquals("VALIDATION_ERROR", badStatus.code());
    }

    private UserAccount account(Long id, UserRole role, Long storeId, UserStatus status, long version) {
        return new UserAccount(id, "user" + id + "@example.com", "hash", "User " + id, "",
                role, storeId, status, false, LocalDateTime.of(2026, 11, 22, 0, 0), version);
    }
}
