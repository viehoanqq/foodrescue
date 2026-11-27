package com.foodrescue.store.infrastructure.adapter.out.persistence;

import com.foodrescue.store.application.port.out.IReviewRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.ReviewView;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReviewRepository implements IReviewRepository {
    private static final RowMapper<ReviewView.Item> ITEM = (rs, row) -> new ReviewView.Item(
            rs.getLong("id"), rs.getLong("order_id"), rs.getInt("rating"), rs.getString("comment"),
            rs.getTimestamp("created_at").toLocalDateTime());
    private final JdbcTemplate jdbc;

    public JdbcReviewRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public ReviewView.Item save(Long storeId, Long customerId, ReviewView.Create review) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO reviews(order_id,customer_id,store_id,rating,comment) VALUES (?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, review.orderId());
                statement.setLong(2, customerId);
                statement.setLong(3, storeId);
                statement.setInt(4, review.rating());
                statement.setString(5, review.comment());
                return statement;
            }, key);
        } catch (DuplicateKeyException ex) {
            throw new StoreException(409, "REVIEW_EXISTS", "Đơn hàng này đã được đánh giá");
        }
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("Missing generated review id");
        return jdbc.queryForObject("SELECT id,order_id,customer_id,store_id,rating,comment,created_at "
                + "FROM reviews WHERE id=?", ITEM, id.longValue());
    }

    @Override
    public ReviewView.Page findByStoreId(Long storeId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE store_id=?", Long.class, storeId);
        BigDecimal average = jdbc.queryForObject(
                "SELECT COALESCE(ROUND(AVG(rating),2),0) FROM reviews WHERE store_id=?", BigDecimal.class, storeId);
        List<ReviewView.Item> content = jdbc.query("SELECT id,order_id,customer_id,store_id,rating,comment,created_at "
                + "FROM reviews WHERE store_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                ITEM, storeId, size, (long) page * size);
        long count = total == null ? 0 : total;
        return new ReviewView.Page(content, average == null ? BigDecimal.ZERO : average,
                page, size, count, (count + size - 1) / size);
    }
}
