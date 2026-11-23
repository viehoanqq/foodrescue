package com.foodrescue.order.infrastructure.adapter.out.persistence;

import com.foodrescue.order.application.port.out.IOrderReviewRepository;
import com.foodrescue.order.domain.model.ReviewEligibility;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderReviewRepository implements IOrderReviewRepository {
    private final JdbcTemplate jdbc;

    public JdbcOrderReviewRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<ReviewEligibility> findEligibility(Long orderId, Long customerId) {
        return jdbc.query("SELECT id,store_id,customer_id,status FROM orders WHERE id=? AND customer_id=?",
                (rs, row) -> new ReviewEligibility("COMPLETED".equals(rs.getString("status")),
                        rs.getLong("id"), rs.getLong("store_id"), rs.getLong("customer_id"),
                        rs.getString("status")), orderId, customerId).stream().findFirst();
    }
}
