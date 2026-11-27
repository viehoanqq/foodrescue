package com.foodrescue.identity.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.ISpringDataAuditRepository;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.ISpringDataInboxRepository;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.AuditLogEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditEventListenerTest {
    private final ISpringDataInboxRepository inbox = mock(ISpringDataInboxRepository.class);
    private final ISpringDataAuditRepository audit = mock(ISpringDataAuditRepository.class);
    private final AuditEventListener listener = new AuditEventListener(inbox, audit, new ObjectMapper());

    @Test
    void newEventWritesInboxAndAuditInTheSameHandler() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(inbox.insertIfAbsent(eq(MessagingNames.IDENTITY_AUDIT_CONSUMER),
                eq(eventId.toString()), any())).thenReturn(1);

        listener.receive(event(eventId));

        ArgumentCaptor<AuditLogEntity> saved = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(audit).save(saved.capture());
        assertEquals("STORE_APPROVED", saved.getValue().getAction());
        assertEquals(5L, saved.getValue().getActorId());
        assertEquals(7L, saved.getValue().getEntityId());
    }

    @Test
    void duplicateEventDoesNotWriteAuditAgain() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(inbox.insertIfAbsent(eq(MessagingNames.IDENTITY_AUDIT_CONSUMER),
                eq(eventId.toString()), any())).thenReturn(0);

        listener.receive(event(eventId));

        verifyNoInteractions(audit);
    }

    private String event(UUID id) {
        return "{\"eventId\":\"" + id + "\",\"eventType\":\"audit.action.v1\",\"actorId\":5,"
                + "\"payload\":{\"action\":\"STORE_APPROVED\",\"entityType\":\"STORE\","
                + "\"entityId\":7,\"amount\":10000,\"detail\":\"approved\"}}";
    }
}
