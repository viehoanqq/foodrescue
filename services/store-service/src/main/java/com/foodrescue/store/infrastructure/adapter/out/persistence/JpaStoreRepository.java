package com.foodrescue.store.infrastructure.adapter.out.persistence;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.store.application.port.out.IOutboxPort;
import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.*;
import com.foodrescue.store.infrastructure.adapter.out.persistence.entity.StoreEntity;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaStoreRepository implements IStoreRepository {
    private final IJpaStoreRepository stores;
    private final IOutboxPort outbox;
    private final EntityManager em;

    public JpaStoreRepository(IJpaStoreRepository stores, IOutboxPort outbox, EntityManager em) {
        this.stores = stores; this.outbox = outbox; this.em = em;
    }

    @Override public Optional<OwnedStore> findByOwnerId(Long ownerId) {
        return stores.findByOwnerId(ownerId).map(s -> new OwnedStore(s.getId(), s.getOwnerId(), s.getStatus()));
    }

    @Override public Optional<StoreView.Registration> findRegistration(Long ownerId) {
        return stores.findByOwnerId(ownerId).map(s -> new StoreView.Registration(s.getId(), s.getStatus(),
                s.getRejectReason(), s.getCreatedAt(), s.getName(), s.getDescription(), s.getPhone(),
                s.getAddress(), s.getLatitude(), s.getLongitude(), s.getImageUrl(), s.getOpenTime(),
                s.getCloseTime(), s.getBankName(), mask(s.getBankAccountNo()), s.getBankAccountName()));
    }

    @Override public Optional<StoreView.Profile> findProfileByOwnerId(Long ownerId) {
        return stores.findByOwnerId(ownerId).map(this::profile);
    }

    @Override @Transactional
    public StoreView.Profile updateProfile(Long ownerId, StoreProfileUpdate input) {
        StoreEntity s = stores.findLockedByOwnerId(ownerId).orElseThrow(() ->
                new StoreException(409, "STORE_NOT_EDITABLE", "Cửa hàng chưa thể chỉnh sửa hồ sơ"));
        if (!List.of("APPROVED", "LOCKED").contains(s.getStatus()))
            throw new StoreException(409, "STORE_NOT_EDITABLE", "Cửa hàng chưa thể chỉnh sửa hồ sơ");
        apply(s, input);
        s.setAggregateVersion(s.getAggregateVersion() + 1);
        stores.saveAndFlush(s);
        outbox.append(EventEnvelope.create(MessagingNames.AUDIT_ACTION_EVENT, "Store", s.getId().toString(),
                s.getAggregateVersion(), UUID.randomUUID(), null, ownerId,
                Map.of("action", "STORE_PROFILE_UPDATED", "entityType", "STORE", "entityId", s.getId(),
                        "detail", "Store profile updated")));
        return profile(s);
    }

    @Override public Optional<StoreView.Detail> findApprovedById(Long storeId) {
        return stores.findByIdAndStatus(storeId, "APPROVED").map(s -> {
            Object[] r = rating(storeId);
            return new StoreView.Detail(s.getId(), s.getName(), s.getDescription(), s.getPhone(),
                    s.getAddress(), s.getImageUrl(), s.getLatitude(), s.getLongitude(),
                    (BigDecimal) r[0], ((Number) r[1]).longValue(), s.getOpenTime(), s.getCloseTime());
        });
    }

    @Override public StoreView.Page findApproved(BigDecimal latitude, BigDecimal longitude,
            BigDecimal radiusKm, int page, int size) {
        String distance = "ST_Distance_Sphere(POINT(s.longitude,s.latitude),POINT(:lon,:lat))/1000";
        String filter = "s.status='APPROVED'" + (radiusKm == null ? "" : " AND " + distance + " <= :radius");
        var countQuery = em.createNativeQuery("SELECT COUNT(*) FROM stores s WHERE " + filter);
        if (radiusKm != null) { countQuery.setParameter("lon", longitude); countQuery.setParameter("lat", latitude);
            countQuery.setParameter("radius", radiusKm); }
        long total = ((Number) countQuery.getSingleResult()).longValue();
        String sql = "SELECT s.id,s.name,s.address,s.image_url,s.latitude,s.longitude," +
                "COALESCE(r.rating,0),COALESCE(r.review_count,0)," +
                (latitude == null ? "NULL" : distance) +
                " FROM stores s LEFT JOIN (SELECT store_id,ROUND(AVG(rating),2) rating," +
                "COUNT(*) review_count FROM reviews GROUP BY store_id) r ON r.store_id=s.id WHERE " + filter +
                (latitude == null ? " ORDER BY s.id DESC" : " ORDER BY 9,s.id");
        var query = em.createNativeQuery(sql).setFirstResult(page * size).setMaxResults(size);
        if (latitude != null) { query.setParameter("lon", longitude); query.setParameter("lat", latitude); }
        if (radiusKm != null) query.setParameter("radius", radiusKm);
        @SuppressWarnings("unchecked") List<Object[]> rows = query.getResultList();
        List<StoreView.Summary> content = rows.stream().map(r -> new StoreView.Summary(
                ((Number) r[0]).longValue(), (String) r[1], (String) r[2], (String) r[3],
                (BigDecimal) r[4], (BigDecimal) r[5], toDecimal(r[6]), ((Number) r[7]).longValue(),
                r[8] == null ? null : ((Number) r[8]).doubleValue())).toList();
        return new StoreView.Page(content, page, size, total, (total + size - 1) / size);
    }

    @Override @Transactional
    public Registered save(Long ownerId, StoreRegistration d, String licenseUrl, String imageUrl) {
        StoreEntity s = new StoreEntity();
        s.setOwnerId(ownerId); s.setName(d.storeName().trim()); s.setDescription(d.description());
        s.setPhone(d.storePhone().trim()); s.setAddress(d.address().trim());
        s.setLatitude(d.latitude()); s.setLongitude(d.longitude()); s.setImageUrl(imageUrl);
        s.setLicenseImageUrl(licenseUrl); s.setOpenTime(d.openTime()); s.setCloseTime(d.closeTime());
        s.setStatus("PENDING"); s.setCommissionRate(new BigDecimal("15.00"));
        s.setBankName(d.bankName().trim()); s.setBankAccountNo(d.bankAccountNo().trim());
        s.setBankAccountName(d.bankAccountName().trim()); s.setCreatedAt(LocalDateTime.now());
        s.setAggregateVersion(1L);
        try { stores.saveAndFlush(s); }
        catch (DataIntegrityViolationException ex) {
            throw new StoreException(409, "STORE_ALREADY_EXISTS", "Tài khoản đã đăng ký cửa hàng");
        }
        return new Registered(s.getId(), s.getCreatedAt());
    }

    static void apply(StoreEntity s, StoreProfileUpdate d) {
        s.setName(d.name()); s.setDescription(d.description()); s.setPhone(d.phone());
        s.setAddress(d.address()); s.setLatitude(d.latitude()); s.setLongitude(d.longitude());
        s.setOpenTime(d.openTime()); s.setCloseTime(d.closeTime()); s.setBankName(d.bankName());
        if (d.bankAccountNo() != null) s.setBankAccountNo(d.bankAccountNo());
        s.setBankAccountName(d.bankAccountName());
    }

    static String mask(String value) {
        if (value == null || value.isEmpty()) return null;
        return value.length() <= 4 ? "*".repeat(value.length())
                : "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    private StoreView.Profile profile(StoreEntity s) {
        return new StoreView.Profile(s.getId(), s.getOwnerId(), s.getName(), s.getDescription(),
                s.getPhone(), s.getAddress(), s.getLatitude(), s.getLongitude(), s.getImageUrl(),
                s.getOpenTime(), s.getCloseTime(), s.getStatus(), s.getRejectReason(),
                s.getCommissionRate(), s.getBankName(), mask(s.getBankAccountNo()),
                s.getBankAccountName(), s.getCreatedAt());
    }

    private Object[] rating(Long storeId) {
        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT COALESCE(ROUND(AVG(rating),2),0),COUNT(*) FROM reviews WHERE store_id=:id")
                .setParameter("id", storeId).getSingleResult();
        return new Object[] { toDecimal(row[0]), row[1] };
    }

    private static BigDecimal toDecimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }
}
