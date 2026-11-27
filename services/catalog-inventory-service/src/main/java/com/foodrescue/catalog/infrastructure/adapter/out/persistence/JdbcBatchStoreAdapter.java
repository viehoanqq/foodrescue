package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.foodrescue.catalog.application.port.out.IBatchStorePort;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class JdbcBatchStoreAdapter implements IBatchStorePort {
    private final JdbcTemplate jdbc;

    public JdbcBatchStoreAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Long requireSingleStore(List<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty() || batchIds.contains(null)
                || batchIds.stream().anyMatch(id -> id <= 0)
                || new HashSet<>(batchIds).size() != batchIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid batch IDs");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(batchIds.size(), "?"));
        List<Long> stores = jdbc.query(
                "SELECT store_id FROM rescue_batches WHERE id IN (" + placeholders + ")",
                (rs, row) -> rs.getLong(1), batchIds.toArray());
        if (stores.size() != batchIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found");
        }
        Long storeId = stores.get(0);
        if (stores.stream().anyMatch(id -> !storeId.equals(id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Batches belong to different stores");
        }
        return storeId;
    }
}
