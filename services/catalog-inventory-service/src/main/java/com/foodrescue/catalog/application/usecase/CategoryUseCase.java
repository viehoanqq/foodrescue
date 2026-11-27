package com.foodrescue.catalog.application.usecase;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.catalog.application.port.out.ICategoryRepository;
import com.foodrescue.catalog.application.port.out.IOutboxPort;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.domain.model.Actor;
import com.foodrescue.catalog.domain.model.Category;
import com.foodrescue.catalog.domain.model.CategoryDraft;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh mục thuộc từng cửa hàng. Chủ cửa hàng và nhân viên chỉ thao tác trên cửa hàng APPROVED của mình;
 * mọi thay đổi ghi audit.action.v1 vào outbox cùng transaction với thay đổi danh mục.
 */
@Service
public class CategoryUseCase {
    private static final int MAX_NAME = 100;
    private static final int MAX_DESCRIPTION = 1000;
    private static final int MAX_IMAGE_URL = 255;
    private static final int MAX_SORT_ORDER = 9999;
    private static final int MAX_AUDIT_DETAIL = 500;

    private final ICategoryRepository categories;
    private final CatalogStoreAccess storeAccess;
    private final IOutboxPort outbox;

    public CategoryUseCase(ICategoryRepository categories, CatalogStoreAccess storeAccess, IOutboxPort outbox) {
        this.categories = categories;
        this.storeAccess = storeAccess;
        this.outbox = outbox;
    }

    /** Công khai: chỉ cửa hàng APPROVED. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public List<Category> listPublic(Long storeId) {
        return categories.findByStore(storeAccess.requireApprovedStoreId(storeId));
    }

    @Transactional(readOnly = true)
    public List<Category> listMine(Actor actor) {
        return categories.findByStore(storeAccess.requireManagedStoreId(actor));
    }

    @Transactional(readOnly = true)
    public List<Category> listAdmin(Long storeId) {
        requireStoreId(storeId);
        return categories.findAllByStore(storeId);
    }

    @Transactional
    public Category create(Actor actor, CategoryDraft input) {
        Long storeId = storeAccess.requireManagedStoreId(actor);
        return createForStore(actor, storeId, input);
    }

    @Transactional
    public Category createAdmin(Actor actor, Long storeId, CategoryDraft input) {
        requireAdmin(actor);
        requireStoreId(storeId);
        storeAccess.requireExistingStore(storeId);
        return createForStore(actor, storeId, input);
    }

    private Category createForStore(Actor actor, Long storeId, CategoryDraft input) {
        CategoryDraft draft = clean(input);
        if (categories.nameTaken(storeId, draft.name(), null)) throw nameExists();
        Category saved = categories.save(new Category(null, storeId, draft.name(), draft.description(),
                draft.imageUrl(), draft.sortOrder(), 1L));
        audit(actor, saved, "CATEGORY_CREATED", saved.version(), "storeId=" + storeId + "; name=" + saved.name());
        return saved;
    }

    @Transactional
    public Category update(Actor actor, Long id, CategoryDraft input) {
        Long storeId = storeAccess.requireManagedStoreId(actor);
        return updateForStore(actor, storeId, id, input);
    }

    @Transactional
    public Category updateAdmin(Actor actor, Long storeId, Long id, CategoryDraft input) {
        requireAdmin(actor);
        requireStoreId(storeId);
        return updateForStore(actor, storeId, id, input);
    }

    private Category updateForStore(Actor actor, Long storeId, Long id, CategoryDraft input) {
        CategoryDraft draft = clean(input);
        Category current = categories.findForUpdate(storeId, id).orElseThrow(this::notFound);
        if (categories.nameTaken(storeId, draft.name(), id)) throw nameExists();
        Category saved = categories.save(current.withDraft(draft));
        audit(actor, saved, "CATEGORY_UPDATED", saved.version(), "storeId=" + storeId + "; name=" + saved.name());
        return saved;
    }

    @Transactional
    public void delete(Actor actor, Long id) {
        Long storeId = storeAccess.requireManagedStoreId(actor);
        deleteForStore(actor, storeId, id);
    }

    @Transactional
    public void deleteAdmin(Actor actor, Long storeId, Long id) {
        requireAdmin(actor);
        requireStoreId(storeId);
        deleteForStore(actor, storeId, id);
    }

    private void deleteForStore(Actor actor, Long storeId, Long id) {
        Category current = categories.findForUpdate(storeId, id).orElseThrow(this::notFound);
        categories.delete(current);
        audit(actor, current, "CATEGORY_DELETED", current.version() + 1,
                "storeId=" + storeId + "; name=" + current.name());
    }

    private void requireAdmin(Actor actor) {
        if (!"ADMIN".equals(actor.role())) {
            throw CatalogException.forbidden("FORBIDDEN", "Bạn không có quyền thực hiện thao tác này");
        }
    }

    private void requireStoreId(Long storeId) {
        if (storeId == null || storeId < 1) {
            throw CatalogException.badRequest("VALIDATION_ERROR", "storeId phải lớn hơn 0");
        }
    }

    private CategoryDraft clean(CategoryDraft input) {
        String name = input.name() == null ? "" : input.name().trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw CatalogException.badRequest("VALIDATION_ERROR", "Tên danh mục phải từ 1 đến 100 ký tự");
        }
        String description = blankToNull(input.description());
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw CatalogException.badRequest("VALIDATION_ERROR", "Mô tả tối đa 1000 ký tự");
        }
        String imageUrl = blankToNull(input.imageUrl());
        if (imageUrl != null && imageUrl.length() > MAX_IMAGE_URL) {
            throw CatalogException.badRequest("VALIDATION_ERROR", "Đường dẫn ảnh tối đa 255 ký tự");
        }
        int sortOrder = input.sortOrder() == null ? 0 : input.sortOrder();
        if (sortOrder < 0 || sortOrder > MAX_SORT_ORDER) {
            throw CatalogException.badRequest("VALIDATION_ERROR", "Thứ tự hiển thị phải từ 0 đến 9999");
        }
        return new CategoryDraft(name, description, imageUrl, sortOrder);
    }

    private void audit(Actor actor, Category category, String action, long version, String detail) {
        String safeDetail = detail.length() > MAX_AUDIT_DETAIL ? detail.substring(0, MAX_AUDIT_DETAIL) : detail;
        outbox.append(EventEnvelope.create(MessagingNames.AUDIT_ACTION_EVENT, "Category",
                category.id().toString(), version, UUID.randomUUID(), null, actor.userId(),
                Map.of("action", action, "entityType", "CATEGORY", "entityId", category.id(),
                        "detail", safeDetail)));
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CatalogException notFound() {
        return CatalogException.notFound("CATEGORY_NOT_FOUND", "Không tìm thấy danh mục");
    }

    private CatalogException nameExists() {
        return CatalogException.conflict("CATEGORY_NAME_EXISTS", "Tên danh mục đã tồn tại trong cửa hàng");
    }

}
