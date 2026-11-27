package com.foodrescue.identity.application.usecase;

import com.foodrescue.identity.application.port.out.*;
import com.foodrescue.identity.domain.exception.IdentityException;
import com.foodrescue.identity.domain.model.UserAccount;
import com.foodrescue.identity.domain.repository.IUserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentityGoogleLoginTest {
    private final IUserRepository users = mock(IUserRepository.class);
    private final IGoogleIdentityPort google = mock(IGoogleIdentityPort.class);
    private final IAuditRepository audit = mock(IAuditRepository.class);
    private final ITokenPort tokens = mock(ITokenPort.class);
    private final IPasswordPort passwords = mock(IPasswordPort.class);
    private final IUnitOfWork unitOfWork = mock(IUnitOfWork.class);
    private IdentityService service;

    @BeforeEach
    void setUp() {
        when(unitOfWork.execute(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        when(google.verify("valid-token"))
                .thenReturn(new IGoogleIdentityPort.GoogleProfile("google-123", "person@example.com", "Person"));
        service = new IdentityService(users, passwords, tokens, audit, unitOfWork, google,
                mock(IRegistrationOtpPort.class));
    }

    @Test
    void firstGoogleSignInCreatesCustomerWithoutPasswordOrPhone() {
        when(users.save(any())).thenAnswer(inv -> {
            UserAccount draft = inv.getArgument(0);
            return new UserAccount(12L, draft.email(), draft.passwordHash(), draft.fullName(), draft.phone(),
                    draft.role(), draft.storeId(), draft.status(), draft.mustChangePassword(), draft.createdAt(),
                    draft.tokenVersion(), draft.address(), draft.latitude(), draft.longitude(), draft.googleSub());
        });
        when(tokens.issue(12L, 0)).thenReturn("foodrescue-token");

        IdentityService.AuthResult result = service.loginWithGoogle("valid-token");

        assertEquals("foodrescue-token", result.token());
        assertEquals("CUSTOMER", result.user().role());
        assertEquals("google-123", result.user().googleSub());
        assertNull(result.user().passwordHash());
        assertNull(result.user().phone());
        verify(audit).record(12L, "USER_REGISTERED", "USER", 12L, "GOOGLE");
    }

    @Test
    void returningGoogleUserKeepsExistingAccount() {
        UserAccount existing = new UserAccount(12L, "person@example.com", null, "Person", null,
                "CUSTOMER", null, "ACTIVE", false, LocalDateTime.now(), 2,
                null, null, null, "google-123");
        when(users.findByGoogleSub("google-123")).thenReturn(Optional.of(existing));
        when(tokens.issue(12L, 2)).thenReturn("new-token");

        assertEquals("new-token", service.loginWithGoogle("valid-token").token());
        verify(users, never()).save(any());
    }

    @Test
    void promotedStoreOwnerCanStillSignInWithGoogle() {
        UserAccount owner = new UserAccount(12L, "person@example.com", null, "Person", null,
                "STORE_OWNER", null, "ACTIVE", false, LocalDateTime.now(), 3,
                null, null, null, "google-123");
        when(users.findByGoogleSub("google-123")).thenReturn(Optional.of(owner));

        assertEquals("STORE_OWNER", service.loginWithGoogle("valid-token").user().role());
    }

    @Test
    void existingPasswordAccountIsNotAutomaticallyLinkedByEmail() {
        when(users.findByEmail("person@example.com")).thenReturn(Optional.of(
                new UserAccount(5L, "person@example.com", "hash", "Person", "0123456789",
                        "CUSTOMER", null, "ACTIVE", false, LocalDateTime.now(), 0)));

        IdentityException error = assertThrows(IdentityException.class,
                () -> service.loginWithGoogle("valid-token"));

        assertEquals("GOOGLE_LINK_REQUIRED", error.code());
        verify(users, never()).save(any());
    }

    @Test
    void linkGoogleRequiresCurrentPasswordAndMatchingEmail() {
        UserAccount local = new UserAccount(5L, "person@example.com", "hash", "Person", "0123456789",
                "CUSTOMER", null, "ACTIVE", false, LocalDateTime.now(), 0);
        when(users.findById(5L)).thenReturn(Optional.of(local));
        when(passwords.matches("correct-password", "hash")).thenReturn(true);
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IdentityException wrongPassword = assertThrows(IdentityException.class,
                () -> service.linkGoogle(5L, "wrong-password", "valid-token"));
        assertEquals("INVALID_CREDENTIALS", wrongPassword.code());

        UserAccount linked = service.linkGoogle(5L, "correct-password", "valid-token");
        assertEquals("google-123", linked.googleSub());
        verify(audit).record(5L, "GOOGLE_ACCOUNT_LINKED", "USER", 5L, null);

        when(google.verify("other-email-token"))
                .thenReturn(new IGoogleIdentityPort.GoogleProfile("google-456", "other@example.com", "Other"));
        IdentityException wrongEmail = assertThrows(IdentityException.class,
                () -> service.linkGoogle(5L, "correct-password", "other-email-token"));
        assertEquals("GOOGLE_EMAIL_MISMATCH", wrongEmail.code());
    }

    @Test
    void linkGoogleRejectsGoogleAccountBelongingToSomeoneElse() {
        UserAccount local = new UserAccount(5L, "person@example.com", "hash", "Person", "0123456789",
                "CUSTOMER", null, "ACTIVE", false, LocalDateTime.now(), 0);
        when(users.findById(5L)).thenReturn(Optional.of(local));
        when(passwords.matches("correct-password", "hash")).thenReturn(true);
        when(users.findByGoogleSub("google-123")).thenReturn(Optional.of(
                new UserAccount(6L, "other@example.com", null, "Other", null,
                        "CUSTOMER", null, "ACTIVE", false, LocalDateTime.now(), 0,
                        null, null, null, "google-123")));

        IdentityException error = assertThrows(IdentityException.class,
                () -> service.linkGoogle(5L, "correct-password", "valid-token"));
        assertEquals("GOOGLE_ACCOUNT_IN_USE", error.code());
        verify(users, never()).save(any());
    }
}
