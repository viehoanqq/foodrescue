package com.foodrescue.identity.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.identity.domain.model.UserAccount;
import com.foodrescue.identity.domain.repository.IUserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StoreStatusListenerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final IUserRepository users = mock(IUserRepository.class);
    private final StoreStatusListener listener = new StoreStatusListener(new ObjectMapper(), jdbc, users);

    @BeforeEach
    void acceptNewEvent() {
        when(jdbc.update(startsWith("INSERT IGNORE INTO inbox_events"), any(), any(), any())).thenReturn(1);
        when(jdbc.update(startsWith("INSERT IGNORE INTO store_status_projection"),
                any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void approvalPromotesExistingCustomerWithoutSettingStaffStoreId() throws Exception {
        when(users.findById(5L)).thenReturn(Optional.of(account("CUSTOMER", 3)));

        listener.receive(event("APPROVED", 1));

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        assertEquals("STORE_OWNER", saved.getValue().role());
        assertNull(saved.getValue().storeId());
        assertEquals(4, saved.getValue().tokenVersion());
    }

    @Test
    void rejectionRecordsProjectionWithoutChangingIdentityAccount() throws Exception {
        listener.receive(event("REJECTED", 1));

        verifyNoInteractions(users);
        verify(jdbc).update(startsWith("INSERT IGNORE INTO store_status_projection"),
                eq(7L), eq(5L), eq("REJECTED"), eq(1L));
    }

    @Test
    void lockingInvalidatesOwnerAndStaffTokens() throws Exception {
        when(users.findById(5L)).thenReturn(Optional.of(account("STORE_OWNER", 3)));

        listener.receive(event("LOCKED", 2));

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        assertEquals(4, saved.getValue().tokenVersion());
        verify(jdbc).update(startsWith("UPDATE users SET token_version"), eq(7L));
    }

    private UserAccount account(String role, long version) {
        return new UserAccount(5L, "owner@example.com", "hash", "Owner", "0123456789",
                role, null, "ACTIVE", false, LocalDateTime.now(), version);
    }

    private String event(String status, long version) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"store.status-changed.v1\","
                + "\"aggregateType\":\"Store\",\"aggregateId\":\"7\",\"aggregateVersion\":" + version
                + ",\"payload\":{\"storeId\":7,\"ownerId\":5,\"status\":\"" + status
                + "\",\"version\":" + version + "}}";
    }
}
