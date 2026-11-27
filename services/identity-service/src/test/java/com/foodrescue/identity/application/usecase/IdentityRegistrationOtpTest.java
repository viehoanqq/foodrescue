package com.foodrescue.identity.application.usecase;

import com.foodrescue.identity.application.port.out.*;
import com.foodrescue.identity.application.port.out.IEmailOtpPort.OtpType;
import com.foodrescue.identity.domain.exception.IdentityException;
import com.foodrescue.identity.domain.model.UserAccount;
import com.foodrescue.identity.domain.repository.IUserRepository;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentityRegistrationOtpTest {
    private final IUserRepository users = mock(IUserRepository.class);
    private final IEmailOtpPort otp = mock(IEmailOtpPort.class);
    private final IPasswordPort passwords = mock(IPasswordPort.class);
    private final ITokenPort tokens = mock(ITokenPort.class);
    private final IAuditRepository audit = mock(IAuditRepository.class);
    private final IUnitOfWork unitOfWork = mock(IUnitOfWork.class);
    private IdentityService service;

    @BeforeEach
    void setUp() {
        when(unitOfWork.execute(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        when(passwords.hash("password123")).thenReturn("hashed-password");
        when(users.save(any())).thenAnswer(inv -> {
            UserAccount draft = inv.getArgument(0);
            return new UserAccount(42L, draft.email(), draft.passwordHash(), draft.fullName(), draft.phone(),
                    draft.role(), draft.storeId(), draft.status(), draft.mustChangePassword(), draft.createdAt(),
                    draft.tokenVersion());
        });
        service = new IdentityService(users, passwords, tokens, audit, unitOfWork,
                mock(IGoogleIdentityPort.class), otp);
    }

    @Test
    void invalidOtpCannotCreateAccount() {
        doThrow(new IdentityException(400, "INVALID_OTP", "Invalid code"))
                .when(otp).verify(OtpType.REGISTRATION, "person@example.com", "000000");

        assertThrows(IdentityException.class, () -> service.register(
                "PERSON@example.com", "password123", "Person", "0123456789", "000000"));
        verifyNoInteractions(unitOfWork, passwords);
        verify(users, never()).save(any());
    }

    @Test
    void successfulRegistrationConsumesOtpAndCreatesAccount() {
        var result = service.register("PERSON@example.com", "password123", "Person", "0123456789", "123456");

        assertEquals(42L, result.user().id());
        verify(otp).verify(OtpType.REGISTRATION, "person@example.com", "123456");
        verify(otp).consume(OtpType.REGISTRATION, "person@example.com", "123456");
        verify(audit).record(42L, "USER_REGISTERED", "USER", 42L, null);
    }
}
