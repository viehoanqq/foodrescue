package com.foodrescue.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.foodrescue.finance.application.port.out.*;
import com.foodrescue.finance.application.usecase.FinanceUseCase;
import com.foodrescue.finance.domain.exception.FinanceException;
import com.foodrescue.finance.domain.model.FinanceModels.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

class FinanceUseCaseTest {
    final FinanceDependencies deps = mock(FinanceDependencies.class);
    final FinanceLedger ledger = mock(FinanceLedger.class);
    final PaymentGateway gateway = mock(PaymentGateway.class);
    final FinanceUseCase useCase =
            new FinanceUseCase(
                    deps,
                    ledger,
                    gateway,
                    Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneOffset.UTC));
    final UUID correlation = UUID.randomUUID();

    Intent intent(long customer, String status, LocalDateTime deadline) {
        return new Intent(
                1L, "FR1", customer, 2L, new BigDecimal("45000"), status, "UNPAID", deadline);
    }

    @Test
    void rejectsAnotherCustomersOrder() {
        when(deps.intent(1, correlation))
                .thenReturn(intent(9, "PENDING_PAYMENT", LocalDateTime.of(2026, 11, 27, 7, 15)));
        assertEquals(
                "FORBIDDEN",
                assertThrows(
                                FinanceException.class,
                                () -> useCase.payment(8, 1, "key", "127.0.0.1", correlation))
                        .code());
        verifyNoInteractions(gateway);
    }

    @Test
    void repeatReturnsPersistedLinkWithoutCallingDependencies() {
        Link link = new Link(1, 1, "ref", "https://sandbox.test", "PENDING");
        when(ledger.existing(8, "key", 1)).thenReturn(link);
        assertSame(link, useCase.payment(8, 1, "key", "127.0.0.1", correlation));
        verifyNoInteractions(deps, gateway);
    }

    @Test
    void reservesFiveMinutesForLateIpn() {
        when(deps.intent(1, correlation))
                .thenReturn(intent(8, "PENDING_PAYMENT", LocalDateTime.of(2026, 11, 27, 7, 4)));
        assertEquals(
                "PAYMENT_INTENT_INVALID",
                assertThrows(
                                FinanceException.class,
                                () -> useCase.payment(8, 1, "key", "127.0.0.1", correlation))
                        .code());
        verifyNoInteractions(gateway);
    }

    @Test
    void linkDeadlineNeverExceedsOrderSafetyWindow() {
        Intent intent = intent(8, "PENDING_PAYMENT", LocalDateTime.of(2026, 11, 27, 7, 12));
        when(deps.intent(1, correlation)).thenReturn(intent);
        useCase.payment(8, 1, "key", "127.0.0.1", correlation);
        verify(gateway)
                .url(
                        eq(intent),
                        anyString(),
                        eq(LocalDateTime.of(2026, 11, 27, 7, 0)),
                        eq(LocalDateTime.of(2026, 11, 27, 7, 7)),
                        eq("127.0.0.1"));
    }
}
