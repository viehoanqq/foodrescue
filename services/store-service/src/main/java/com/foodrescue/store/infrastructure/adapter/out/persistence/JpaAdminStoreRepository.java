package com.foodrescue.store.infrastructure.adapter.out.persistence;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.store.application.port.out.IAdminStoreRepository;
import com.foodrescue.store.application.port.out.IOutboxPort;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreProfileUpdate;
import com.foodrescue.store.domain.model.StoreView;
import com.foodrescue.store.infrastructure.adapter.out.persistence.entity.StoreEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaAdminStoreRepository implements IAdminStoreRepository {
    private final IJpaStoreRepository stores;
    private final IOutboxPort outbox;

    public JpaAdminStoreRepository(IJpaStoreRepository stores, IOutboxPort outbox) {
        this.stores = stores; this.outbox = outbox;
    }

    @Override public StoreView.AdminPage findForAdmin(String status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<StoreEntity> rows = status == null || status.isBlank()
                ? stores.findAll(pageable) : stores.findByStatus(status, pageable);
        return new StoreView.AdminPage(rows.map(this::admin).getContent(), page, size,
                rows.getTotalElements(), rows.getTotalPages());
    }

    @Override public Optional<StoreView.Admin> findForAdminById(Long id) {
        return stores.findById(id).map(this::admin);
    }

    @Override @Transactional
    public StoreView.Admin changeStatus(Long id, String expectedStatus, String nextStatus,
            BigDecimal commissionRate, String rejectReason, Long actorId) {
        StoreEntity s = locked(id);
        if (!expectedStatus.equals(s.getStatus()))
            throw new StoreException(409, "STORE_STATUS_CONFLICT", "Trạng thái cửa hàng đã thay đổi");
        s.setStatus(nextStatus);
        if (commissionRate != null) s.setCommissionRate(commissionRate);
        s.setRejectReason(rejectReason);
        s.setAggregateVersion(s.getAggregateVersion() + 1);
        stores.saveAndFlush(s);
        long version = s.getAggregateVersion();
        UUID correlationId = UUID.randomUUID();
        outbox.append(EventEnvelope.create(MessagingNames.STORE_STATUS_CHANGED_EVENT, "Store", id.toString(),
                version, correlationId, null, actorId, Map.of("storeId", id, "ownerId", s.getOwnerId(),
                        "status", nextStatus, "commissionRate", s.getCommissionRate(), "version", version,
                        "changedAt", Instant.now().toString())));
        String action = "LOCKED".equals(expectedStatus) && "APPROVED".equals(nextStatus)
                ? "STORE_UNLOCKED" : "STORE_" + nextStatus;
        outbox.append(EventEnvelope.create(MessagingNames.AUDIT_ACTION_EVENT, "Store", id.toString(),
                version, correlationId, null, actorId, Map.of("action", action, "entityType", "STORE",
                        "entityId", id, "detail", rejectReason == null ? "status=" + nextStatus : rejectReason)));
        return admin(s);
    }

    @Override @Transactional
    public StoreView.Admin changeCommission(Long id, BigDecimal commissionRate, Long actorId) {
        StoreEntity s = locked(id);
        if (!"APPROVED".equals(s.getStatus()) && !"LOCKED".equals(s.getStatus()))
            throw new StoreException(409, "STORE_STATUS_CONFLICT", "Cửa hàng chưa được duyệt");
        s.setCommissionRate(commissionRate);
        s.setAggregateVersion(s.getAggregateVersion() + 1);
        stores.saveAndFlush(s);
        outbox.append(EventEnvelope.create(MessagingNames.AUDIT_ACTION_EVENT, "Store", id.toString(),
                s.getAggregateVersion(), UUID.randomUUID(), null, actorId,
                Map.of("action", "STORE_COMMISSION_UPDATED", "entityType", "STORE", "entityId", id,
                        "detail", "commissionRate=" + commissionRate)));
        return admin(s);
    }

    @Override @Transactional
    public StoreView.Admin updateProfile(Long id, StoreProfileUpdate input, Long actorId) {
        StoreEntity s = stores.findLockedById(id).orElseThrow(() ->
                new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng"));
        JpaStoreRepository.apply(s, input);
        s.setAggregateVersion(s.getAggregateVersion() + 1);
        stores.saveAndFlush(s);
        outbox.append(EventEnvelope.create(MessagingNames.AUDIT_ACTION_EVENT, "Store", id.toString(),
                s.getAggregateVersion(), UUID.randomUUID(), null, actorId,
                Map.of("action", "STORE_PROFILE_UPDATED_BY_ADMIN", "entityType", "STORE",
                        "entityId", id, "detail", "Admin updated store profile")));
        return admin(s);
    }

    private StoreEntity locked(Long id) {
        return stores.findLockedById(id).orElseThrow(() ->
                new StoreException(409, "STORE_STATUS_CONFLICT", "Trạng thái cửa hàng đã thay đổi"));
    }

    private StoreView.Admin admin(StoreEntity s) {
        return new StoreView.Admin(s.getId(), s.getOwnerId(), s.getName(), s.getDescription(),
                s.getPhone(), s.getAddress(), s.getLatitude(), s.getLongitude(), s.getImageUrl(),
                s.getOpenTime(), s.getCloseTime(), s.getStatus(), s.getRejectReason(),
                s.getCommissionRate(), s.getBankName(), JpaStoreRepository.mask(s.getBankAccountNo()),
                s.getBankAccountName(), s.getCreatedAt(), s.getLicenseImageUrl());
    }
}
