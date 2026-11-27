package com.foodrescue.identity.application.usecase;

import com.foodrescue.identity.application.port.out.*;
import com.foodrescue.identity.application.port.out.IEmailOtpPort.OtpType;
import com.foodrescue.identity.domain.exception.IdentityException;
import com.foodrescue.identity.domain.model.UserAccount;
import com.foodrescue.identity.domain.repository.IUserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentityPasswordResetOtpTest {
    private final IUserRepository users = mock(IUserRepository.class);
    private final IEmailOtpPort emailOtp = mock(IEmailOtpPort.class);
    private final IPasswordPort passwords = mock(IPasswordPort.class);
    private final ITokenPort tokens = mock(ITokenPort.class);
    private final IAuditRepository audit = mock(IAuditRepository.class);
    private final IUnitOfWork unitOfWork = mock(IUnitOfWork.class);
    private IdentityService service;

    @BeforeEach
    void setUp() {
        when(unitOfWork.execute(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        when(passwords.hash("NewSecret123")).thenReturn("hashed-new-secret");
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new IdentityService(users, passwords, tokens, audit, unitOfWork,
                mock(IGoogleIdentityPort.class), emailOtp);
    }

    @Test
    void sendOtpRejectsUnknownEmail() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        IdentityException ex = assertThrows(IdentityException.class,
                () -> service.sendPasswordResetOtp("Missing@example.com"));

        assertEquals(404, ex.status());
        assertEquals("EMAIL_NOT_FOUND", ex.code());
        verifyNoInteractions(emailOtp);
    }

    @Test
    void sendOtpRejectsLockedAccount() {
        when(users.findByEmail("locked@example.com")).thenReturn(Optional.of(
                user(7L, "locked@example.com", "LOCKED", true, 3L, null)));

        IdentityException ex = assertThrows(IdentityException.class,
                () -> service.sendPasswordResetOtp("locked@example.com"));

        assertEquals(403, ex.status());
        assertEquals("ACCOUNT_LOCKED", ex.code());
        verifyNoInteractions(emailOtp);
    }

    @Test
    void sendOtpNormalizesEmailForActiveAccount() {
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(
                user(7L, "user@example.com", "ACTIVE", false, 1L, null)));

        service.sendPasswordResetOtp("  USER@Example.com ");

        verify(emailOtp).send(OtpType.PASSWORD_RESET, "user@example.com");
    }

    @Test
    void invalidOtpDoesNotResetPassword() {
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(
                user(7L, "user@example.com", "ACTIVE", false, 1L, null)));
        doThrow(new IdentityException(400, "INVALID_OTP", "Invalid code"))
                .when(emailOtp).verify(OtpType.PASSWORD_RESET, "user@example.com", "000000");

        assertThrows(IdentityException.class,
                () -> service.resetPasswordWithOtp("USER@example.com", "000000", "NewSecret123"));

        verifyNoInteractions(unitOfWork, passwords);
        verify(users, never()).save(any());
    }

    @Test
    void lockedAccountCannotResetPassword() {
        when(users.findByEmail("locked@example.com")).thenReturn(Optional.of(
                user(7L, "locked@example.com", "LOCKED", false, 1L, null)));

        IdentityException ex = assertThrows(IdentityException.class,
                () -> service.resetPasswordWithOtp("locked@example.com", "123456", "NewSecret123"));

        assertEquals("ACCOUNT_LOCKED", ex.code());
        verifyNoInteractions(emailOtp, unitOfWork, passwords);
    }

    @Test
    void successfulResetConsumesOtpUpdatesPasswordRevokesTokensAndWritesAudit() {
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(
                user(7L, "user@example.com", "ACTIVE", true, 4L, "google-sub-1")));

        service.resetPasswordWithOtp("USER@example.com", "654321", "NewSecret123");

        verify(emailOtp).verify(OtpType.PASSWORD_RESET, "user@example.com", "654321");
        verify(emailOtp).consume(OtpType.PASSWORD_RESET, "user@example.com", "654321");

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        assertEquals("hashed-new-secret", saved.getValue().passwordHash());
        assertFalse(saved.getValue().mustChangePassword());
        assertEquals(5L, saved.getValue().tokenVersion());
        assertEquals("google-sub-1", saved.getValue().googleSub());
        verify(audit).record(7L, "USER_PASSWORD_RESET", "USER", 7L, "EMAIL_OTP");
    }

    private UserAccount user(Long id, String email, String status, boolean mustChange, long version, String googleSub) {
        return new UserAccount(id, email, "old-hash", "User", "0123456789", "CUSTOMER",
                null, status, mustChange, LocalDateTime.of(2026, 11, 20, 10, 0), version,
                null, null, null, googleSub);
    }
}
