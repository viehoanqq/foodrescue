package com.foodrescue.store.infrastructure.adapter.out.persistence;

import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.buildingblocks.events.MessagingNames;
import com.foodrescue.store.application.port.out.IAdminStoreRepository;
import com.foodrescue.store.application.port.out.IOutboxPort;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAdminStoreRepository implements IAdminStoreRepository {
    private static final String ADMIN_COLUMNS = "id,owner_id,name,description,phone,address,latitude," +
            "longitude,image_url,open_time,close_time,status,reject_reason,commission_rate," +
            "bank_name,bank_account_no,bank_account_name,created_at,license_image_url";

    private final JdbcTemplate jdbc;
    private final IOutboxPort outbox;

    public JdbcAdminStoreRepository(JdbcTemplate jdbc, IOutboxPort outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Override
    public StoreView.AdminPage findForAdmin(String status, int page, int size) {
        boolean filtered = status != null && !status.isBlank();
        String where = filtered ? " WHERE status=?" : "";
        Long total = filtered ? jdbc.queryForObject("SELECT COUNT(*) FROM stores" + where, Long.class, status)
                : jdbc.queryForObject("SELECT COUNT(*) FROM stores", Long.class);
        var rows = filtered
                ? jdbc.query("SELECT " + ADMIN_COLUMNS + " FROM stores" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                    (rs, row) -> admin(rs), status, size, (long) page * size)
                : jdbc.query("SELECT " + ADMIN_COLUMNS + " FROM stores ORDER BY id DESC LIMIT ? OFFSET ?",
                    (rs, row) -> admin(rs), size, (long) page * size);
        long elements = total == null ? 0 : total;
        return new StoreView.AdminPage(rows, page, size, elements, (elements + size - 1) / size);
    }

    @Override
    public Optional<StoreView.Admin> findForAdminById(Long id) {
        return jdbc.query("SELECT " + ADMIN_COLUMNS + " FROM stores WHERE id=?",
                (rs, row) -> admin(rs), id).stream().findFirst();
    }

    private StoreView.Admin admin(ResultSet rs) throws SQLException {
        return new StoreView.Admin(rs.getLong("id"), rs.getLong("owner_id"), rs.getString("name"),
                rs.getString("description"), rs.getString("phone"), rs.getString("address"),
                rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getString("image_url"),
                rs.getTime("open_time").toLocalTime(), rs.getTime("close_time").toLocalTime(),
                rs.getString("status"), rs.getString("reject_reason"), rs.getBigDecimal("commission_rate"),
                rs.getString("bank_name"), maskAccount(rs.getString("bank_account_no")),
                rs.getString("bank_account_name"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getString("license_image_url"));
    }

    private String maskAccount(String account) {
        if (account == null || account.isEmpty()) return null;
        if (account.length() <= 4) return "*".repeat(account.length());
        return "*".repeat(account.length() - 4) + account.substring(account.length() - 4);
    }

    @Override
    @Transactional
    public StoreView.Admin changeStatus(Long id, String expectedStatus, String nextStatus,
            BigDecimal commissionRate, String rejectReason, Long actorId) {
        int changed = jdbc.update("UPDATE stores SET status=?,commission_rate=COALESCE(?,commission_rate)," +
                        "reject_reason=?,aggregate_version=aggregate_version+1 WHERE id=? AND status=?",
                nextStatus, commissionRate, rejectReason, id, expectedStatus);
        if (changed != 1) throw new StoreException(409, "STORE_STATUS_CONFLICT", "Trạng thái cửa hàng đã thay đổi");
        var row = jdbc.queryForMap("SELECT owner_id,commission_rate,aggregate_version FROM stores WHERE id=?", id);
        long version = ((Number) row.get("aggregate_version")).longValue();
        Long ownerId = ((Number) row.get("owner_id")).longValue();
        BigDecimal rate = (BigDecimal) row.get("commission_rate");
        UUID correlationId = UUID.randomUUID();
        outbox.append(EventEnvelope.create(MessagingNames.STORE_STATUS_CHANGED_EVENT, "Store", id.toString(),
                version, correlationId, null, actorId, Map.of("storeId", id, "ownerId", ownerId,
                        "status", nextStatus, "commissionRate", rate, "version", version,
                        "changedAt", Instant.now().toString())));
        String action = "LOCKED".equals(expectedStatus) && "APPROVED".equals(nextStatus)
                ? "STORE_UNLOCKED" : "STORE_" + nextStatus;
        outbox.append(EventEnvelope.create(MessagingNames.AUDIT_ACTION_EVENT, "Store", id.toString(),
                version, correlationId, null, actorId, Map.of("action", action,
                        "entityType", "STORE", "entityId", id, "detail",
                        rejectReason == null ? "status=" + nextStatus : rejectReason)));
        return findForAdminById(id).orElseThrow();
    }

    @Override
    @Transactional
    public StoreView.Admin changeCommission(Long id, BigDecimal commissionRate, Long actorId) {
        int changed = jdbc.update("UPDATE stores SET commission_rate=?,aggregate_version=aggregate_version+1 " +
                "WHERE id=? AND status IN ('APPROVED','LOCKED')", commissionRate, id);
        if (changed != 1) throw new StoreException(409, "STORE_STATUS_CONFLICT", "Cửa hàng chưa được duyệt");
        long version = jdbc.queryForObject("SELECT aggregate_version FROM stores WHERE id=?", Long.class, id);
        outbox.append(EventEnvelope.create(MessagingNames.AUDIT_ACTION_EVENT, "Store", id.toString(),
                version, UUID.randomUUID(), null, actorId, Map.of("action", "STORE_COMMISSION_UPDATED",
                        "entityType", "STORE", "entityId", id, "detail", "commissionRate=" + commissionRate)));
        return findForAdminById(id).orElseThrow();
    }
}
