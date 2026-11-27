package com.foodrescue.finance.infrastructure.adapter.out.persistence;

import jakarta.persistence.EntityManager;

import org.hibernate.query.NativeQuery;
import org.hibernate.query.TupleTransformer;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Native PostgreSQL operations for projections, reports and transport; business CRUD uses
 * repositories.
 */
@Repository
@Transactional
public class JpaFinanceQueries {
    private final EntityManager em;

    public JpaFinanceQueries(EntityManager em) {
        this.em = em;
    }

    private jakarta.persistence.Query query(String sql, Object... args) {
        // Hibernate requires numbered positional parameters in native queries.
        StringBuilder numbered = new StringBuilder();
        int index = 0;
        for (char c : sql.toCharArray()) {
            if (c == '?') numbered.append('?').append(++index);
            else numbered.append(c);
        }
        var q = em.createNativeQuery(numbered.toString());
        for (int i = 0; i < args.length; i++) q.setParameter(i + 1, args[i]);
        return q;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return query(sql, args)
                .unwrap(NativeQuery.class)
                .setTupleTransformer(
                        (TupleTransformer<Map<String, Object>>)
                                (tuple, aliases) -> {
                                    Map<String, Object> row = new LinkedHashMap<>();
                                    for (int i = 0; i < tuple.length; i++)
                                        row.put(aliases[i], tuple[i]);
                                    return row;
                                })
                .getResultList();
    }

    public Map<String, Object> queryForMap(String sql, Object... args) {
        var rows = queryForList(sql, args);
        if (rows.size() != 1) throw new IllegalStateException("Expected one database row");
        return rows.get(0);
    }

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        Object v = query(sql, args).getSingleResult();
        if (v == null) return null;
        if (type == Long.class && v instanceof Number n) return type.cast(n.longValue());
        if (type == Integer.class && v instanceof Number n) return type.cast(n.intValue());
        if (type == String.class) return type.cast(v.toString());
        return type.cast(v);
    }

    public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
        return query(sql, args).getResultList().stream()
                .map(v -> type == Long.class ? type.cast(((Number) v).longValue()) : type.cast(v))
                .toList();
    }

    public int update(String sql, Object... args) {
        return query(sql, args).executeUpdate();
    }

    public void advisoryLock(String key) {
        query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", key).getSingleResult();
    }

    public void refresh(Object entity) {
        em.refresh(entity);
    }

    public void flush() {
        em.flush();
    }
}
