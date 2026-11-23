package com.foodrescue.store.infrastructure.adapter.out.persistence;

import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreRegistration;
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
