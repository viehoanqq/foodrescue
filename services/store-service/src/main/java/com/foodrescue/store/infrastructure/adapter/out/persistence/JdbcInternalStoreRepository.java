package com.foodrescue.store.infrastructure.adapter.out.persistence;

import com.foodrescue.store.application.port.out.IInternalStoreRepository;
import com.foodrescue.store.domain.model.StoreInternalView;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInternalStoreRepository implements IInternalStoreRepository {
    private final JdbcTemplate jdbc;

    public JdbcInternalStoreRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StoreInternalView.CheckoutPolicy> findCheckoutPolicy(Long storeId) {
        return jdbc.query("SELECT id,status,commission_rate,aggregate_version,updated_at " +
                        "FROM stores WHERE id=?",
                (rs, row) -> new StoreInternalView.CheckoutPolicy(rs.getLong("id"),
                        rs.getString("status"), rs.getBigDecimal("commission_rate"),
                        rs.getLong("aggregate_version"), rs.getTimestamp("updated_at").toLocalDateTime()),
                storeId).stream().findFirst();
    }

    @Override
    public Optional<StoreInternalView.PayoutAccount> findPayoutAccount(Long storeId) {
        return jdbc.query("SELECT id,status,bank_name,bank_account_no,bank_account_name,updated_at " +
                        "FROM stores WHERE id=?",
                (rs, row) -> new StoreInternalView.PayoutAccount(rs.getLong("id"),
                        rs.getString("status"), rs.getString("bank_name"),
                        rs.getString("bank_account_no"), rs.getString("bank_account_name"),
                        rs.getTimestamp("updated_at").toLocalDateTime()),
                storeId).stream().findFirst();
    }

    @Override
    public StoreInternalView.Counts countByStatus() {
        return jdbc.queryForObject("SELECT " +
                        "COUNT(CASE WHEN status='APPROVED' THEN 1 END) approved," +
                        "COUNT(CASE WHEN status='LOCKED' THEN 1 END) locked," +
                        "COUNT(CASE WHEN status='PENDING' THEN 1 END) pending FROM stores",
                (rs, row) -> new StoreInternalView.Counts(rs.getLong("approved"),
                        rs.getLong("locked"), rs.getLong("pending")));
    }
}
