package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.application.port.out.IEmailOtpPort.OtpType;
import com.foodrescue.identity.application.port.out.IEmailSenderPort;
import com.foodrescue.identity.domain.exception.IdentityException;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.EmailOtpEntity;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.EmailOtpId;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JpaEmailOtpAdapterTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final Map<EmailOtpId, EmailOtpEntity> store = new HashMap<>();
    private final IJpaEmailOtpRepository repository = mock(IJpaEmailOtpRepository.class);
    private final IEmailSenderPort emailSender = mock(IEmailSenderPort.class);
    private JpaEmailOtpAdapter adapter;

    @BeforeEach
    void setUp() {
        when(repository.findById(any(EmailOtpId.class)))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
        when(repository.findLocked(anyString(), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(new EmailOtpId(inv.getArgument(0), inv.getArgument(1)))));
        when(repository.incrementAttempts(anyString(), anyString()))
                .thenAnswer(inv -> {
                    EmailOtpEntity e = store.get(new EmailOtpId(inv.getArgument(0), inv.getArgument(1)));
                    if (e != null && e.getAttempts() < 5) {
                        e.setAttempts(e.getAttempts() + 1);
                        return 1;
                    }
                    return 0;
                });
        when(repository.saveAndFlush(any(EmailOtpEntity.class))).thenAnswer(inv -> {
            EmailOtpEntity e = inv.getArgument(0);
            store.put(e.getId(), e);
            return e;
        });
        doAnswer(inv -> {
            EmailOtpEntity e = inv.getArgument(0);
            store.remove(e.getId());
            return null;
        }).when(repository).delete(any(EmailOtpEntity.class));

        adapter = new JpaEmailOtpAdapter(repository, emailSender, SECRET);
    }

    @Test
    void sendsBothTypesIndependentlyAsHtmlForSameEmailAndAllowsSingleConsume() {
        adapter.send(OtpType.REGISTRATION, "user@example.com");
        adapter.send(OtpType.PASSWORD_RESET, "user@example.com");

        ArgumentCaptor<String> subjects = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> plainBodies = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlBodies = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(2)).ensureConfigured();
        verify(emailSender, times(2)).sendHtml(
                eq("user@example.com"), subjects.capture(), plainBodies.capture(), htmlBodies.capture());

        assertEquals("FoodRescue - Mã xác minh đăng ký", subjects.getAllValues().get(0));
        assertEquals("FoodRescue - Mã xác minh đặt lại mật khẩu", subjects.getAllValues().get(1));

        String regCode = extractCode(plainBodies.getAllValues().get(0));
        String resetCode = extractCode(plainBodies.getAllValues().get(1));
        assertTrue(htmlBodies.getAllValues().get(0).contains(regCode));
        assertTrue(htmlBodies.getAllValues().get(1).contains(resetCode));
        assertFalse(htmlBodies.getAllValues().get(0).contains("<script"));

        // Code from password reset must not verify under registration type and vice versa
        assertThrows(IdentityException.class, () -> adapter.verify(OtpType.REGISTRATION, "user@example.com", resetCode));
        assertThrows(IdentityException.class, () -> adapter.verify(OtpType.PASSWORD_RESET, "user@example.com", regCode));

        adapter.verify(OtpType.REGISTRATION, "user@example.com", regCode);
        adapter.consume(OtpType.REGISTRATION, "user@example.com", regCode);
        assertFalse(store.containsKey(new EmailOtpId("REGISTRATION", "user@example.com")));
        assertTrue(store.containsKey(new EmailOtpId("PASSWORD_RESET", "user@example.com")));

        adapter.verify(OtpType.PASSWORD_RESET, "user@example.com", resetCode);
        adapter.consume(OtpType.PASSWORD_RESET, "user@example.com", resetCode);
        assertTrue(store.isEmpty());
    }

    @Test
    void blocksResendWithinCooldownAndLocksAfterFiveBadAttempts() {
        adapter.send(OtpType.PASSWORD_RESET, "user@example.com");
        IdentityException rateLimited = assertThrows(IdentityException.class,
                () -> adapter.send(OtpType.PASSWORD_RESET, "user@example.com"));
        assertEquals(429, rateLimited.status());
        assertEquals("OTP_RATE_LIMITED", rateLimited.code());

        for (int i = 0; i < 5; i++) {
            IdentityException invalid = assertThrows(IdentityException.class,
                    () -> adapter.verify(OtpType.PASSWORD_RESET, "user@example.com", "000000"));
            assertEquals("INVALID_OTP", invalid.code());
        }
        IdentityException exceeded = assertThrows(IdentityException.class,
                () -> adapter.verify(OtpType.PASSWORD_RESET, "user@example.com", "000000"));
        assertEquals(429, exceeded.status());
        assertEquals("OTP_ATTEMPTS_EXCEEDED", exceeded.code());
    }

    @Test
    void rejectsExpiredOtp() {
        adapter.send(OtpType.PASSWORD_RESET, "user@example.com");
        store.get(new EmailOtpId("PASSWORD_RESET", "user@example.com"))
                .setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        IdentityException expired = assertThrows(IdentityException.class,
                () -> adapter.verify(OtpType.PASSWORD_RESET, "user@example.com", "123456"));
        assertEquals("INVALID_OTP", expired.code());
    }

    private String extractCode(String text) {
        Matcher matcher = Pattern.compile("(\\d{6})").matcher(text);
        assertTrue(matcher.find());
        return matcher.group(1);
    }
}
