package com.foodrescue.order.infrastructure.adapter.out.persistence;

import com.foodrescue.order.application.port.out.ICheckoutIdempotencyPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcCheckoutIdempotencyAdapter implements ICheckoutIdempotencyPort {
    private final JdbcTemplate jdbc;

    public JdbcCheckoutIdempotencyAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long claim(Long customerId, String key) {
        String scoped = scopedKey(customerId, key);
        jdbc.update("INSERT IGNORE INTO idempotency_keys (`key`, target_type, target_id) VALUES (?, 'ORDER', 'PENDING')", scoped);
        String target = jdbc.queryForObject(
                "SELECT target_id FROM idempotency_keys WHERE `key` = ? AND target_type = 'ORDER' FOR UPDATE",
                String.class, scoped);
        if (target == null || "PENDING".equals(target)) return null;
        return Long.valueOf(target);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Long customerId, String key, Long orderId) {
        int updated = jdbc.update(
                "UPDATE idempotency_keys SET target_id = ? WHERE `key` = ? AND target_type = 'ORDER' AND target_id = 'PENDING'",
                orderId.toString(), scopedKey(customerId, key));
        if (updated != 1) throw new IllegalStateException("Checkout idempotency claim was lost");
    }

    private String scopedKey(Long customerId, String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((customerId + ":" + key).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot hash checkout key", ex);
        }
    }
}
