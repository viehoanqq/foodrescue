package com.foodrescue.finance.infrastructure.adapter.out.persistence;

import static com.foodrescue.finance.infrastructure.adapter.out.persistence.JpaFinanceLedger.*;

import com.foodrescue.finance.application.port.out.FinanceReports;
import com.foodrescue.finance.domain.exception.FinanceException;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Repository
@Transactional(
        readOnly = true,
        isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class JpaFinanceReports implements FinanceReports {
    private final JpaFinanceQueries db;

    public JpaFinanceReports(JpaFinanceQueries db) {
        this.db = db;
    }

    public Map<String, Object> list(
            String resource, Long store, String status, int page, int size) {
        pagination(page, size);
        table(resource);
        String where = " WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (store != null) {
            if (!"payouts".equals(resource)) throw new IllegalArgumentException();
            where += " AND store_id=?";
            args.add(store);
        }
        if (status != null && !status.isBlank()) {
            where += " AND status=?";
            args.add(status);
        }
        Long total =
                db.queryForObject(
                        "SELECT count(*) FROM " + resource + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        var rows =
                db.queryForList(
                        "SELECT * FROM " + resource + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                        args.toArray());
        return Map.of(
                "items",
                rows.stream().map(r -> view(resource, r)).toList(),
                "total",
                total,
                "page",
                page,
                "size",
                size);
    }

    public Map<String, Object> detail(String resource, long id, Long store) {
        table(resource);
        String sql = "SELECT * FROM " + resource + " WHERE id=?";
        var rows =
                store == null
                        ? db.queryForList(sql, id)
                        : db.queryForList(sql + " AND store_id=?", id, store);
        if (rows.isEmpty()) throw new FinanceException(404, "NOT_FOUND", "Không tìm thấy bản ghi");
        var result = view(resource, rows.get(0));
        if ("payouts".equals(resource))
            result.put(
                    "items",
                    db
                            .queryForList(
                                    "SELECT"
                                        + " order_id,gross_amount,commission_amount,net_amount,active"
                                        + " FROM payout_items WHERE payout_id=? ORDER BY order_id",
                                    id)
                            .stream()
                            .map(JpaFinanceLedger::camel)
                            .toList());
        if ("refunds".equals(resource))
            result.put(
                    "payment",
                    camel(
                            db.queryForMap(
                                    "SELECT id,txn_ref,gateway_txn_no,amount,paid_at FROM payments"
                                            + " WHERE id=?",
                                    rows.get(0).get("payment_id"))));
        return result;
    }

    public Map<String, Object> revenue(Long store, LocalDate from, LocalDate to) {
        dates(from, to);
        List<Object> args =
                new ArrayList<>(List.of(from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
        String filter = " WHERE s.completed_at>=? AND s.completed_at<? AND " + eligible();
        if (store != null) {
            filter += " AND s.store_id=?";
            args.add(store);
        }
        var result =
                camel(
                        db.queryForMap(
                                "SELECT count(*) AS order_count,coalesce(sum(total_amount),0) AS"
                                        + " gross_amount,coalesce(sum(commission_amount),0) AS"
                                        + " commission_amount,coalesce(sum(store_earning),0) AS"
                                        + " net_amount FROM order_snapshots s"
                                        + filter,
                                args.toArray()));
        result.put(
                "daily",
                db
                        .queryForList(
                                "SELECT CAST(completed_at AS date) AS day,count(*) AS"
                                        + " order_count,sum(total_amount) AS"
                                        + " gross_amount,sum(commission_amount) AS"
                                        + " commission_amount,sum(store_earning) AS net_amount FROM"
                                        + " order_snapshots s"
                                        + filter
                                        + " GROUP BY CAST(completed_at AS date) ORDER BY day",
                                args.toArray())
                        .stream()
                        .map(JpaFinanceLedger::camel)
                        .toList());
        result.put("from", from);
        result.put("to", to);
        return result;
    }

    public Map<String, Object> statement(
            long store, LocalDate from, LocalDate to, int page, int size) {
        dates(from, to);
        pagination(page, size);
        String filter =
                " WHERE s.store_id=? AND s.completed_at>=? AND s.completed_at<? AND " + eligible();
        Object[] args = {store, from.atStartOfDay(), to.plusDays(1).atStartOfDay()};
        Long total =
                db.queryForObject(
                        "SELECT count(*) FROM order_snapshots s" + filter, Long.class, args);
        var rows =
                db.queryForList(
                        """
SELECT s.order_id,s.status,s.completed_at,s.total_amount,s.commission_amount,s.store_earning,
p.id AS payout_id,p.payout_code,p.status AS payout_status FROM order_snapshots s
LEFT JOIN payout_items i ON i.order_id=s.order_id AND i.active LEFT JOIN payouts p ON p.id=i.payout_id
"""
                                + filter
                                + " ORDER BY s.completed_at DESC,s.order_id DESC LIMIT ? OFFSET ?",
                        store,
                        args[1],
                        args[2],
                        size,
                        (long) page * size);
        return Map.of(
                "items",
                rows.stream().map(JpaFinanceLedger::camel).toList(),
                "total",
                total,
                "page",
                page,
                "size",
                size);
    }

    public Map<String, Object> reconciliation() {
        Map<String, Object> r = new LinkedHashMap<>();
        BigDecimal received =
                sum("SELECT coalesce(sum(amount),0) FROM payments WHERE status='SUCCESS'");
        r.put("received", received);
        r.put(
                "refunded",
                sum("SELECT coalesce(sum(amount),0) FROM refunds WHERE status='COMPLETED'"));
        r.put(
                "refundPending",
                sum("SELECT coalesce(sum(amount),0) FROM refunds WHERE status='PENDING'"));
        r.put(
                "paidOut",
                sum("SELECT coalesce(sum(net_amount),0) FROM payouts WHERE status='PAID'"));
        r.put(
                "payoutPending",
                sum("SELECT coalesce(sum(net_amount),0) FROM payouts WHERE status='PENDING'"));
        r.put(
                "unallocated",
                sum(
                        "SELECT coalesce(sum(s.store_earning),0) FROM order_snapshots s WHERE "
                                + eligible()
                                + " AND NOT EXISTS(SELECT 1 FROM payout_items i WHERE"
                                + " i.order_id=s.order_id AND i.active)"));
        r.put(
                "commission",
                sum(
                        "SELECT coalesce(sum(s.commission_amount),0) FROM order_snapshots s WHERE "
                                + eligible()));
        r.put(
                "unfulfilled",
                sum(
                        "SELECT coalesce(sum(p.amount),0) FROM payments p JOIN order_snapshots s ON"
                            + " s.order_id=p.order_id WHERE p.status='SUCCESS' AND"
                            + " s.status='CONFIRMED' AND NOT EXISTS(SELECT 1 FROM refunds r WHERE"
                            + " r.order_id=p.order_id)"));
        BigDecimal accounted =
                r.entrySet().stream()
                        .filter(e -> !e.getKey().equals("received"))
                        .map(e -> (BigDecimal) e.getValue())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        r.put("difference", received.subtract(accounted));
        r.put(
                "balance",
                received.subtract((BigDecimal) r.get("refunded"))
                        .subtract((BigDecimal) r.get("paidOut")));
        r.put(
                "pendingOutbox",
                db.queryForObject(
                        "SELECT count(*) FROM outbox_events WHERE published_at IS NULL",
                        Long.class));
        r.put(
                "snapshotUpdatedAt",
                db.queryForObject(
                        "SELECT max(updated_at)::text FROM order_snapshots", String.class));
        return r;
    }

    private BigDecimal sum(String sql) {
        return db.queryForObject(sql, BigDecimal.class);
    }

    private Map<String, Object> view(String resource, Map<String, Object> row) {
        var result = "payouts".equals(resource) ? safePayout(row) : camel(row);
        result.remove("paymentUrl");
        result.remove("idempotencyKey");
        return result;
    }

    private void table(String s) {
        if (!Set.of("payments", "refunds", "payouts").contains(s))
            throw new IllegalArgumentException();
    }

    private void dates(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusYears(5)))
            throw new FinanceException(400, "VALIDATION_ERROR", "Khoảng ngày tối đa 5 năm");
    }

    private void pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new FinanceException(400, "VALIDATION_ERROR", "page >= 0, size từ 1 đến 100");
    }
}
