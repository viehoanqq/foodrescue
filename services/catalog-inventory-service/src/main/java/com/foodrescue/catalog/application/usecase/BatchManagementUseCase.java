package com.foodrescue.catalog.application.usecase;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.catalog.application.port.out.IBatchManagementRepository;
import com.foodrescue.catalog.application.port.out.IOutboxPort;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.domain.model.Actor;
import com.foodrescue.catalog.domain.model.BatchDraft;
import com.foodrescue.catalog.domain.model.RescueBatch;
import com.foodrescue.catalog.domain.model.StockMovement;
import com.foodrescue.catalog.domain.model.enums.BatchStatus;
import com.foodrescue.catalog.domain.model.enums.StockMovementReason;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchManagementUseCase {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_QUANTITY = 100_000;

    private final IBatchManagementRepository batches;
    private final CatalogStoreAccess storeAccess;
    private final IOutboxPort outbox;

    public BatchManagementUseCase(IBatchManagementRepository batches, CatalogStoreAccess storeAccess,
                                  IOutboxPort outbox) {
        this.batches = batches;
        this.storeAccess = storeAccess;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<RescueBatch> listMine(Actor actor) {
        return batches.findActiveByStore(storeAccess.requireManagedStoreId(actor));
    }

    @Transactional(readOnly = true)
    public Page<RescueBatch> listAdmin(Long storeId, Pageable pageable) {
        if (storeId == null) return batches.findAllAdmin(pageable);
        if (storeId < 1) throw invalid("storeId phải lớn hơn 0");
        return batches.findAdminByStore(storeId, pageable);
    }

    @Transactional
    public RescueBatch create(Actor actor, BatchDraft input) {
        long storeId = storeAccess.requireManagedStoreId(actor);
        BatchDraft draft = clean(input, true);
        requireActiveCategory(storeId, draft.categoryId());
        LocalDateTime now = LocalDateTime.now(VIETNAM);
        RescueBatch batch = batches.save(new RescueBatch(null, storeId, draft.categoryId(), draft.title(),
                draft.description(), draft.imageUrl(), draft.originalPrice(), draft.rescuePrice(),
                draft.quantity(), draft.quantity(), draft.pickupStart(), draft.pickupEnd(), BatchStatus.AVAILABLE,
                now, 1, false));
        batches.appendMovement(new StockMovement(null, batch.id(), batch.quantity(), StockMovementReason.LISTED, null,
                actor.userId(), batch.remainingQuantity(), "Tạo túi giải cứu", now));
        audit(actor, batch, "BATCH_CREATED", "storeId=" + storeId + "; quantity=" + batch.quantity());
        return batch;
    }

    @Transactional
    public RescueBatch update(Actor actor, Long batchId, BatchDraft input) {
        long storeId = storeAccess.requireManagedStoreId(actor);
        RescueBatch current = batches.findForUpdate(storeId, batchId).orElseThrow(this::batchNotFound);
        BatchDraft draft = clean(input, false);
        if (!batches.categoryBelongsToStore(storeId, draft.categoryId())) {
            throw CatalogException.notFound("CATEGORY_NOT_FOUND", "Không tìm thấy danh mục của cửa hàng");
        }
        if (!draft.categoryId().equals(current.categoryId())) requireActiveCategory(storeId, draft.categoryId());
        if (draft.quantity() != current.quantity()) {
            throw invalid("Thay đổi tồn kho phải dùng API điều chỉnh tồn");
        }
        if (current.status() == null || !current.status().isAvailable()) {
            throw CatalogException.conflict("BATCH_STATUS_CONFLICT", "Chỉ sửa được túi đang bán");
        }
        if (current.remainingQuantity() != current.quantity()
                && (!current.rescuePrice().equals(draft.rescuePrice())
                    || !current.originalPrice().equals(draft.originalPrice())
                    || !current.pickupStart().equals(draft.pickupStart())
                    || !current.pickupEnd().equals(draft.pickupEnd()))) {
            throw CatalogException.conflict("BATCH_HAS_ORDERS",
                    "Không thể đổi giá hoặc giờ nhận sau khi đã phát sinh giao dịch tồn kho");
        }
        if ((!current.pickupStart().equals(draft.pickupStart())
                || !current.pickupEnd().equals(draft.pickupEnd()))
                && !draft.pickupStart().isAfter(LocalDateTime.now(VIETNAM))) {
            throw invalid("Giờ bắt đầu nhận mới phải ở tương lai");
        }
        RescueBatch updated = new RescueBatch(current.id(), storeId, draft.categoryId(), draft.title(),
                draft.description(), draft.imageUrl(), draft.originalPrice(), draft.rescuePrice(),
                current.quantity(), current.remainingQuantity(), draft.pickupStart(), draft.pickupEnd(),
                current.status(), current.createdAt(), current.version() + 1, false);
        RescueBatch saved = batches.save(updated);
        audit(actor, saved, "BATCH_UPDATED", "storeId=" + storeId);
        return saved;
    }

    @Transactional
    public RescueBatch adjustStock(Actor actor, Long batchId, int amount, String reason, String note) {
        long storeId = storeAccess.requireManagedStoreId(actor);
        RescueBatch current = batches.findForUpdate(storeId, batchId).orElseThrow(this::batchNotFound);
        if (amount < 1 || amount > MAX_QUANTITY) throw invalid("Số lượng điều chỉnh phải từ 1 đến 100000");
        StockMovementReason adjustment = StockMovementReason.parseManualAdjustment(reason)
                .orElseThrow(() -> invalid("reason phải là ADDED hoặc REMOVED"));
        if (note != null && note.length() > 255) throw invalid("Ghi chú tối đa 255 ký tự");
        if (current.status() == null || !current.status().isStockAdjustable()) {
            throw CatalogException.conflict("BATCH_STATUS_CONFLICT", "Không thể điều chỉnh tồn của túi đã hủy hoặc hết hạn");
        }
        if (!current.pickupEnd().isAfter(LocalDateTime.now(VIETNAM))) {
            throw CatalogException.conflict("BATCH_EXPIRED", "Đã quá giờ nhận, không thể điều chỉnh tồn");
        }
        boolean add = adjustment == StockMovementReason.ADDED;
        if (!add && amount > current.remainingQuantity()) {
            throw CatalogException.conflict("STOCK_NOT_ENOUGH", "Không thể giảm tồn nhiều hơn số lượng còn lại");
        }
        if (add && current.quantity() > MAX_QUANTITY - amount) {
            throw invalid("Tổng số lượng của túi không thể vượt quá 100000");
        }
        int delta = add ? amount : -amount;
        int remaining = current.remainingQuantity() + delta;
        int quantity = current.quantity() + (add ? amount : 0);
        BatchStatus status = BatchStatus.fromRemainingQuantity(remaining);
        RescueBatch saved = batches.save(new RescueBatch(current.id(), current.storeId(), current.categoryId(),
                current.title(), current.description(), current.imageUrl(), current.originalPrice(),
                current.rescuePrice(), quantity, remaining, current.pickupStart(), current.pickupEnd(), status,
                current.createdAt(), current.version() + 1, false));
        String cleanNote = note == null || note.isBlank() ? adjustment.name() : note.trim();
        batches.appendMovement(new StockMovement(null, saved.id(), delta, adjustment, null, actor.userId(),
                remaining, cleanNote, LocalDateTime.now(VIETNAM)));
        audit(actor, saved, "STOCK_" + adjustment.name(), "changeQty=" + delta + "; remaining=" + remaining);
        return saved;
    }

    @Transactional
    public RescueBatch cancel(Actor actor, Long batchId, String reason, boolean admin) {
        long storeId = admin ? -1 : storeAccess.requireManagedStoreId(actor);
        RescueBatch current = admin
                ? batches.findAdminForUpdate(batchId).orElseThrow(this::batchNotFound)
                : batches.findForUpdate(storeId, batchId).orElseThrow(this::batchNotFound);
        if (reason == null || reason.isBlank() || reason.trim().length() > 255) {
            throw invalid("Lý do hủy phải từ 1 đến 255 ký tự");
        }
        if (current.status() == null || !current.status().isCancellable()) {
            throw CatalogException.conflict("BATCH_STATUS_CONFLICT", "Không thể hủy túi ở trạng thái hiện tại");
        }
        if (batches.hasHeldReservations(batchId)) {
            throw CatalogException.conflict("BATCH_HAS_ACTIVE_RESERVATION",
                    "Túi đang được giữ trong đơn hàng, chưa thể hủy");
        }
        LocalDateTime now = LocalDateTime.now(VIETNAM);
        RescueBatch cancelled = new RescueBatch(current.id(), current.storeId(), current.categoryId(),
                current.title(), current.description(), current.imageUrl(), current.originalPrice(),
                current.rescuePrice(), current.quantity(), 0, current.pickupStart(), current.pickupEnd(),
                BatchStatus.CANCELLED, current.createdAt(), current.version() + 1, true);
        RescueBatch saved = batches.save(cancelled);
        if (current.remainingQuantity() > 0) {
            batches.appendMovement(new StockMovement(null, current.id(), -current.remainingQuantity(),
                    StockMovementReason.REMOVED, null, actor.userId(), 0, limitNote("Hủy túi: " + reason.trim()), now));
        }
        audit(actor, saved, "BATCH_CANCELLED", "storeId=" + saved.storeId() + "; reason=" + reason.trim());
        outbox.append(EventEnvelope.create("catalog.batch-cancelled.v1", "RescueBatch",
                saved.id().toString(), saved.version(), UUID.randomUUID(), null, actor.userId(),
                Map.of("batchId", saved.id(), "storeId", saved.storeId(), "reason", reason.trim(),
                        "cancelledAt", OffsetDateTime.now(VIETNAM).toString())));
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> movements(Actor actor, Long batchId, Pageable pageable) {
        long storeId = storeAccess.requireManagedStoreId(actor);
        batches.findForRead(storeId, batchId).orElseThrow(this::batchNotFound);
        return batches.movements(batchId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> movementsAdmin(Long batchId, Pageable pageable) {
        return batches.movements(batchId, pageable);
    }

    private BatchDraft clean(BatchDraft input, boolean creating) {
        if (input == null || input.categoryId() == null || input.categoryId() < 1) {
            throw invalid("categoryId không hợp lệ");
        }
        String title = input.title() == null ? "" : input.title().trim();
        if (title.isEmpty() || title.length() > 150) throw invalid("Tên túi phải từ 1 đến 150 ký tự");
        String description = input.description() == null || input.description().isBlank()
                ? null : input.description().trim();
        if (description != null && description.length() > 2000) throw invalid("Mô tả tối đa 2000 ký tự");
        String imageUrl = input.imageUrl() == null || input.imageUrl().isBlank() ? null : input.imageUrl().trim();
        if (imageUrl != null && imageUrl.length() > 255) throw invalid("Đường dẫn ảnh tối đa 255 ký tự");
        BigDecimal original = input.originalPrice();
        BigDecimal rescue = input.rescuePrice();
        if (original == null || rescue == null || original.signum() <= 0 || rescue.signum() <= 0
                || rescue.multiply(BigDecimal.valueOf(100))
                        .compareTo(original.multiply(BigDecimal.valueOf(60))) > 0) {
            throw invalid("Giá túi phải dương và giá giải cứu không vượt quá 60% giá gốc");
        }
        Integer quantity = input.quantity();
        if (quantity == null || quantity < 1 || quantity > MAX_QUANTITY) {
            throw invalid("Số lượng phải từ 1 đến 100000");
        }
        LocalDateTime now = LocalDateTime.now(VIETNAM);
        if (input.pickupStart() == null || input.pickupEnd() == null
                || !input.pickupEnd().isAfter(input.pickupStart()) || !input.pickupEnd().isAfter(now)
                || (creating && !input.pickupStart().isAfter(now))) {
            throw invalid("Khung giờ nhận không hợp lệ");
        }
        return new BatchDraft(input.categoryId(), title, description, imageUrl, original, rescue,
                quantity, input.pickupStart(), input.pickupEnd());
    }

    private void requireActiveCategory(long storeId, Long categoryId) {
        if (!batches.activeCategoryExists(storeId, categoryId)) {
            throw CatalogException.notFound("CATEGORY_NOT_FOUND", "Không tìm thấy danh mục đang hoạt động");
        }
    }

    private void audit(Actor actor, RescueBatch batch, String action, String detail) {
        outbox.append(EventEnvelope.create(MessagingNames.AUDIT_ACTION_EVENT, "RescueBatch",
                batch.id().toString(), batch.version(), UUID.randomUUID(), null, actor.userId(),
                Map.of("action", action, "entityType", "BATCH", "entityId", batch.id(),
                        "detail", detail.length() > 500 ? detail.substring(0, 500) : detail)));
    }

    private CatalogException batchNotFound() {
        return CatalogException.notFound("BATCH_NOT_FOUND", "Không tìm thấy túi giải cứu");
    }

    private CatalogException invalid(String message) {
        return CatalogException.badRequest("VALIDATION_ERROR", message);
    }

    private String limitNote(String note) {
        return note.length() > 255 ? note.substring(0, 255) : note;
    }
}
