package com.foodrescue.order.infrastructure.adapter.out.persistence;

import com.foodrescue.order.application.port.out.ICheckoutIdempotencyPort;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaCheckoutIdempotencyAdapter implements ICheckoutIdempotencyPort {
    private final EntityManager em;
    public JpaCheckoutIdempotencyAdapter(EntityManager em) { this.em = em; }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Long claim(Long customerId, String key) {
        String scoped = scopedKey(customerId, key);
        em.createNativeQuery("INSERT IGNORE INTO idempotency_keys (`key`,target_type,target_id) " +
                "VALUES (:key,'ORDER','PENDING')").setParameter("key", scoped).executeUpdate();
        String target = (String) em.createNativeQuery("SELECT target_id FROM idempotency_keys " +
                "WHERE `key`=:key AND target_type='ORDER' FOR UPDATE")
                .setParameter("key", scoped).getSingleResult();
        return "PENDING".equals(target) ? null : Long.valueOf(target);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Long customerId, String key, Long orderId) {
        int updated = em.createNativeQuery("UPDATE idempotency_keys SET target_id=:orderId " +
                "WHERE `key`=:key AND target_type='ORDER' AND target_id='PENDING'")
                .setParameter("orderId", orderId.toString())
                .setParameter("key", scopedKey(customerId, key)).executeUpdate();
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
