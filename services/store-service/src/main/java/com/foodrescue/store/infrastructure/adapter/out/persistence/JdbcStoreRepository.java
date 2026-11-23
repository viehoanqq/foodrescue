package com.foodrescue.store.infrastructure.adapter.out.persistence;

import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreRegistration;
import com.foodrescue.store.domain.model.StoreView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.sql.Statement;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import com.foodrescue.store.domain.model.OwnedStore;
import java.util.Optional;

@Repository
public class JdbcStoreRepository implements IStoreRepository {
    private final JdbcTemplate jdbc;
    public JdbcStoreRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<OwnedStore> findByOwnerId(Long ownerId) {
        return jdbc.query("SELECT id,owner_id,status FROM stores WHERE owner_id=?", (rs, row) ->
                new OwnedStore(rs.getLong("id"), rs.getLong("owner_id"), rs.getString("status")), ownerId)
                .stream().findFirst();
    }

    @Override
    public Optional<StoreView.Registration> findRegistration(Long ownerId) {
        return jdbc.query("SELECT id,status,reject_reason,created_at FROM stores WHERE owner_id=?",
                (rs, row) -> new StoreView.Registration(rs.getLong("id"), rs.getString("status"),
                        rs.getString("reject_reason"), rs.getTimestamp("created_at").toLocalDateTime()), ownerId)
                .stream().findFirst();
    }

    @Override
    public Optional<StoreView.Profile> findProfileByOwnerId(Long ownerId) {
        String sql = "SELECT id,owner_id,name,description,phone,address,latitude,longitude,image_url," +
                "open_time,close_time,status,reject_reason,commission_rate,bank_name,bank_account_no," +
                "bank_account_name,created_at FROM stores WHERE owner_id=?";
        return jdbc.query(sql, (rs, row) -> new StoreView.Profile(
                rs.getLong("id"), rs.getLong("owner_id"), rs.getString("name"),
                rs.getString("description"), rs.getString("phone"), rs.getString("address"),
                rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getString("image_url"),
                rs.getTime("open_time").toLocalTime(), rs.getTime("close_time").toLocalTime(),
                rs.getString("status"), rs.getString("reject_reason"), rs.getBigDecimal("commission_rate"),
                rs.getString("bank_name"), maskAccount(rs.getString("bank_account_no")),
                rs.getString("bank_account_name"), rs.getTimestamp("created_at").toLocalDateTime()), ownerId)
                .stream().findFirst();
    }

    private String maskAccount(String account) {
        if (account == null || account.isEmpty()) return null;
        if (account.length() <= 4) return "*".repeat(account.length());
        return "*".repeat(account.length() - 4) + account.substring(account.length() - 4);
    }

    private static final String RATINGS = " LEFT JOIN (SELECT store_id,ROUND(AVG(rating),2) rating," +
            "COUNT(*) review_count FROM reviews GROUP BY store_id) r ON r.store_id=s.id ";

    @Override
    public Optional<StoreView.Detail> findApprovedById(Long storeId) {
        String sql = "SELECT s.id,s.name,s.description,s.phone,s.address,s.image_url,s.latitude," +
                "s.longitude,s.open_time,s.close_time,COALESCE(r.rating,0) rating," +
                "COALESCE(r.review_count,0) review_count FROM stores s" + RATINGS +
                "WHERE s.status='APPROVED' AND s.id=?";
        return jdbc.query(sql, (rs, row) -> new StoreView.Detail(rs.getLong("id"), rs.getString("name"),
                rs.getString("description"), rs.getString("phone"), rs.getString("address"),
                rs.getString("image_url"), rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"),
                rs.getBigDecimal("rating"), rs.getLong("review_count"),
                rs.getTime("open_time").toLocalTime(), rs.getTime("close_time").toLocalTime()), storeId)
                .stream().findFirst();
    }

    @Override
    public StoreView.Page findApproved(BigDecimal latitude, BigDecimal longitude, BigDecimal radiusKm,
                                       int page, int size) {
        boolean hasLocation = latitude != null;
        String distance = "ST_Distance_Sphere(POINT(s.longitude,s.latitude),POINT(?,?))/1000";
        String filter = "s.status='APPROVED'" +
                (radiusKm == null ? "" : " AND " + distance + " <= ?");
        var countArgs = new ArrayList<Object>();
        if (radiusKm != null) { countArgs.add(longitude); countArgs.add(latitude); countArgs.add(radiusKm); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM stores s WHERE " + filter,
                Long.class, countArgs.toArray());
        String sql = "SELECT s.id,s.name,s.address,s.image_url,s.latitude,s.longitude," +
                "COALESCE(r.rating,0) rating,COALESCE(r.review_count,0) review_count," +
                (hasLocation ? distance : "NULL") + " distance_km FROM stores s" + RATINGS +
                "WHERE " + filter + (hasLocation ? " ORDER BY distance_km,s.id" : " ORDER BY s.id DESC") +
                " LIMIT ? OFFSET ?";
        var args = new ArrayList<Object>();
        if (hasLocation) { args.add(longitude); args.add(latitude); }
        args.addAll(countArgs);
        args.add(size); args.add((long) page * size);
        var content = jdbc.query(sql, (rs, row) -> new StoreView.Summary(rs.getLong("id"),
                rs.getString("name"), rs.getString("address"), rs.getString("image_url"),
                rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getBigDecimal("rating"),
                rs.getLong("review_count"), hasLocation ? rs.getDouble("distance_km") : null), args.toArray());
        long elements = total == null ? 0 : total;
        return new StoreView.Page(content, page, size, elements, (elements + size - 1) / size);
    }

    @Override
    public Registered save(Long ownerId, StoreRegistration d, String licenseUrl, String imageUrl) {
        LocalDateTime createdAt = LocalDateTime.now();
        var keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var ps = connection.prepareStatement("INSERT INTO stores (owner_id,name,description,phone,address,latitude,longitude,image_url,license_image_url,open_time,close_time,status,commission_rate,bank_name,bank_account_no,bank_account_name,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, ownerId);
                ps.setString(2, d.storeName().trim()); ps.setString(3, d.description());
                ps.setString(4, d.storePhone().trim()); ps.setString(5, d.address().trim());
                ps.setBigDecimal(6, d.latitude()); ps.setBigDecimal(7, d.longitude());
                ps.setString(8, imageUrl); ps.setString(9, licenseUrl);
                ps.setObject(10, d.openTime()); ps.setObject(11, d.closeTime());
                ps.setString(12, "PENDING"); ps.setBigDecimal(13, new java.math.BigDecimal("15.00"));
                ps.setString(14, d.bankName().trim()); ps.setString(15, d.bankAccountNo().trim());
                ps.setString(16, d.bankAccountName().trim()); ps.setObject(17, createdAt);
                return ps;
            }, keys);
        } catch (DuplicateKeyException ex) {
            throw new StoreException(409, "STORE_ALREADY_EXISTS", "Tài khoản đã đăng ký cửa hàng");
        }
        return new Registered(keys.getKey().longValue(), createdAt);
    }
}
