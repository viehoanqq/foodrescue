package com.foodrescue.store.infrastructure.adapter.out.persistence;

import com.foodrescue.store.application.port.out.OwnedStorePort;
import com.foodrescue.store.domain.model.OwnedStore;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOwnedStoreAdapter implements OwnedStorePort {
    private final JdbcTemplate jdbc;

    public JdbcOwnedStoreAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<OwnedStore> findByOwnerId(Long ownerId) {
        return jdbc.query("SELECT id,owner_id,status FROM stores WHERE owner_id=?", (rs, row) ->
                new OwnedStore(rs.getLong("id"), rs.getLong("owner_id"), rs.getString("status")), ownerId)
                .stream().findFirst();
    }
}
