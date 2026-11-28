package com.foodrescue.catalog.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.catalog.application.port.out.ICategoryRepository;
import com.foodrescue.catalog.application.port.out.IOutboxPort;
import com.foodrescue.catalog.application.port.out.IStoreOwnerLookupPort;
import com.foodrescue.catalog.application.port.out.IStoreDirectoryPort;
import com.foodrescue.catalog.application.port.out.IStoreProjectionRepository;
import com.foodrescue.catalog.application.port.out.IStoreStatusPort;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.domain.model.Actor;
import com.foodrescue.catalog.domain.model.CatalogStore;
import com.foodrescue.catalog.domain.model.Category;
import com.foodrescue.catalog.domain.model.CategoryDraft;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CategoryUseCaseTest {
    private final ICategoryRepository categories = mock(ICategoryRepository.class);
    private final IStoreProjectionRepository stores = mock(IStoreProjectionRepository.class);
    private final IStoreOwnerLookupPort ownerLookup = mock(IStoreOwnerLookupPort.class);
    private final IStoreStatusPort storeStatus = mock(IStoreStatusPort.class);
    private final IStoreDirectoryPort storeDirectory = mock(IStoreDirectoryPort.class);
    private final IOutboxPort outbox = mock(IOutboxPort.class);
    private final CatalogStoreAccess storeAccess = new CatalogStoreAccess(stores, ownerLookup, storeStatus);
    private final CategoryUseCase useCase = new CategoryUseCase(categories, storeAccess, outbox, storeDirectory);

    private final Actor owner = new Actor(7L, "STORE_OWNER", null);
    private final Actor staff = new Actor(30L, "STORE_STAFF", 15L);
    private final CategoryDraft draft = new CategoryDraft("  Bánh   mì ", "Mô tả", null, null);

    private void approvedStoreOf(long ownerId, long storeId) {
        CatalogStore approved = new CatalogStore(storeId, ownerId, "APPROVED", 2);
        when(stores.findByOwnerId(ownerId)).thenReturn(Optional.of(approved));
        when(storeStatus.findByStoreId(storeId)).thenReturn(Optional.of(approved));
    }

    @SuppressWarnings("unchecked")
    private EventEnvelope<Map<String, Object>> capturedEvent() {
        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(captor.capture());
        return (EventEnvelope<Map<String, Object>>) captor.getValue();
    }

    @Test
    void ownerCreatesCategoryInOwnStoreAndWritesAudit() {
        approvedStoreOf(7L, 15L);
        when(categories.save(any())).thenAnswer(call -> {
            Category c = call.getArgument(0);
            return new Category(100L, c.storeId(), c.name(), c.description(), c.imageUrl(), c.sortOrder(), c.version());
        });

        Category created = useCase.create(owner, draft);

        assertEquals(100L, created.id());
        assertEquals(15L, created.storeId());
        assertEquals("Bánh mì", created.name());
        assertEquals(0, created.sortOrder());
        var event = capturedEvent();
        assertEquals(MessagingNames.AUDIT_ACTION_EVENT, event.eventType());
        assertEquals(7L, event.actorId());
        assertEquals(1L, event.aggregateVersion());
        assertEquals("CATEGORY_CREATED", event.payload().get("action"));
        assertEquals("CATEGORY", event.payload().get("entityType"));
        assertEquals(100L, event.payload().get("entityId"));
    }

    @Test
    void staffUsesStoreFromTokenNotFromClient() {
        CatalogStore approved = new CatalogStore(15L, 7L, "APPROVED", 2);
        when(stores.findByStoreId(15L)).thenReturn(Optional.of(approved));
        when(storeStatus.findByStoreId(15L)).thenReturn(Optional.of(approved));
        when(categories.findByStore(15L)).thenReturn(List.of());

        assertTrue(useCase.listMine(staff).isEmpty());

        verify(categories).findByStore(15L);
    }

    @Test
    void staffCanManageApprovedStoreEvenWhenProjectionIsEmpty() {
        CatalogStore approved = new CatalogStore(15L, null, "APPROVED", 2);
        when(stores.findByStoreId(15L)).thenReturn(Optional.empty());
        when(storeStatus.findByStoreId(15L)).thenReturn(Optional.of(approved));
        when(categories.findByStore(15L)).thenReturn(List.of());

        assertTrue(useCase.listMine(staff).isEmpty());

        verify(storeStatus).findByStoreId(15L);
        verify(categories).findByStore(15L);
    }

    @Test
    void staffOfLockedStoreCannotManage() {
        CatalogStore locked = new CatalogStore(15L, 7L, "LOCKED", 3);
        when(stores.findByStoreId(15L)).thenReturn(Optional.of(locked));
        when(storeStatus.findByStoreId(15L)).thenReturn(Optional.of(locked));

        CatalogException error = assertThrows(CatalogException.class, () -> useCase.create(staff, draft));

        assertEquals("STORE_NOT_APPROVED", error.code());
        verifyNoInteractions(outbox);
        verify(categories, never()).save(any());
    }

    @Test
    void ownerWithoutProjectionCannotManage() {
        when(stores.findByOwnerId(7L)).thenReturn(Optional.empty());
        when(ownerLookup.findByOwnerId(7L)).thenReturn(Optional.empty());

        CatalogException error = assertThrows(CatalogException.class, () -> useCase.create(owner, draft));

        assertEquals(403, error.status());
        assertEquals("STORE_NOT_FOUND", error.code());
    }

    @Test
    void ownerCanResolveExistingStoreThroughStoreDirectoryWhenProjectionIsEmpty() {
        CatalogStore approved = new CatalogStore(15L, 7L, "APPROVED", 2);
        when(stores.findByOwnerId(7L)).thenReturn(Optional.empty());
        when(ownerLookup.findByOwnerId(7L)).thenReturn(Optional.of(approved));
        when(storeStatus.findByStoreId(15L)).thenReturn(Optional.of(approved));
        when(categories.findByStore(15L)).thenReturn(List.of());

        assertTrue(useCase.listMine(owner).isEmpty());

        verify(ownerLookup).findByOwnerId(7L);
        verify(categories).findByStore(15L);
    }

    @Test
    void otherRolesAreRejected() {
        CatalogException error = assertThrows(CatalogException.class,
                () -> useCase.create(new Actor(1L, "CUSTOMER", null), draft));
        assertEquals("FORBIDDEN", error.code());
    }

    @Test
    void duplicateNameInSameStoreIsConflict() {
        approvedStoreOf(7L, 15L);
        when(categories.nameTaken(15L, "Bánh mì", null)).thenReturn(true);

        CatalogException error = assertThrows(CatalogException.class, () -> useCase.create(owner, draft));

        assertEquals(409, error.status());
        assertEquals("CATEGORY_NAME_EXISTS", error.code());
        verifyNoInteractions(outbox);
    }

    @Test
    void blankNameIsRejected() {
        approvedStoreOf(7L, 15L);

        CatalogException error = assertThrows(CatalogException.class,
                () -> useCase.create(owner, new CategoryDraft("   ", null, null, null)));

        assertEquals("VALIDATION_ERROR", error.code());
    }

    @Test
    void updateBumpsVersionAndAudits() {
        approvedStoreOf(7L, 15L);
        Category current = new Category(100L, 15L, "Cũ", null, null, 0, 1L);
        when(categories.findForUpdate(15L, 100L)).thenReturn(Optional.of(current));
        when(categories.save(any())).thenAnswer(call -> call.getArgument(0));

        Category updated = useCase.update(owner, 100L, new CategoryDraft("Mới", "Mô tả", null, 3));

        assertEquals("Mới", updated.name());
        assertEquals(3, updated.sortOrder());
        assertEquals(2L, updated.version());
        var event = capturedEvent();
        assertEquals("CATEGORY_UPDATED", event.payload().get("action"));
        assertEquals(2L, event.aggregateVersion());
    }

    @Test
    void cannotUpdateCategoryOfAnotherStore() {
        approvedStoreOf(7L, 15L);
        when(categories.findForUpdate(15L, 999L)).thenReturn(Optional.empty());

        CatalogException error = assertThrows(CatalogException.class, () -> useCase.update(owner, 999L, draft));

        assertEquals(404, error.status());
        verify(categories, never()).save(any());
    }

    @Test
    void deletingCategoryArchivesItEvenWhenBatchesStillReferenceIt() {
        approvedStoreOf(7L, 15L);
        Category current = new Category(100L, 15L, "Bánh mì", null, null, 0, 1L);
        when(categories.findForUpdate(15L, 100L)).thenReturn(Optional.of(current));

        useCase.delete(owner, 100L);

        verify(categories).delete(current);
        var event = capturedEvent();
        assertEquals("CATEGORY_DELETED", event.payload().get("action"));
        assertEquals(2L, event.aggregateVersion());
    }

    @Test
    void deleteWithoutBatchesAlsoArchivesAndAudits() {
        approvedStoreOf(7L, 15L);
        Category current = new Category(100L, 15L, "Bánh mì", null, null, 0, 1L);
        when(categories.findForUpdate(15L, 100L)).thenReturn(Optional.of(current));

        useCase.delete(owner, 100L);

        verify(categories).delete(current);
        assertEquals("CATEGORY_DELETED", capturedEvent().payload().get("action"));
    }

    @Test
    void publicListHidesStoresThatAreNotApproved() {
        when(storeStatus.findByStoreId(15L)).thenReturn(Optional.of(new CatalogStore(15L, 7L, "LOCKED", 3)));
        when(storeStatus.findByStoreId(16L)).thenReturn(Optional.empty());

        assertEquals("STORE_NOT_FOUND", assertThrows(CatalogException.class, () -> useCase.listPublic(15L)).code());
        assertEquals("STORE_NOT_FOUND", assertThrows(CatalogException.class, () -> useCase.listPublic(16L)).code());
        verify(categories, never()).findByStore(anyLong());
    }

    @Test
    void publicListReturnsCategoriesOfApprovedStore() {
        when(storeStatus.findByStoreId(15L))
                .thenReturn(Optional.of(new CatalogStore(15L, 7L, "APPROVED", 2)));
        Category c = new Category(1L, 15L, "Bánh mì", null, null, 0, 1L);
        when(categories.findByStore(eq(15L))).thenReturn(List.of(c));

        assertEquals(List.of(c), useCase.listPublic(15L));
    }

    @Test
    void publicListWithoutStoreIncludesCategoriesOnlyFromApprovedStores() {
        when(storeDirectory.findApprovedStores(null, null, null)).thenReturn(List.of(
                new com.foodrescue.catalog.domain.model.CatalogStoreSummary(15L, "A", null, null, null,
                        null, null, null, null, 0, null, null, null)));
        Category category = new Category(1L, 15L, "Bánh mì", null, null, 0, 1L);
        when(categories.findByStores(List.of(15L))).thenReturn(List.of(category));

        assertEquals(List.of(category), useCase.listPublicAcrossStores());
    }
}
