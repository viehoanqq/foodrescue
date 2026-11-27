package com.foodrescue.finance.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.finance.application.port.out.FinanceLedger;
import com.foodrescue.finance.domain.exception.FinanceException;
import com.foodrescue.finance.domain.model.FinanceModels.*;
import com.foodrescue.finance.infrastructure.adapter.out.persistence.entity.*;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Repository
@Transactional(readOnly = true)
public class JpaFinanceLedger implements FinanceLedger {
    private final JpaFinanceQueries db;
    private final ObjectMapper mapper;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PayoutRepository payouts;
    private final PayoutItemRepository payoutItems;

    public JpaFinanceLedger(
            JpaFinanceQueries db,
            ObjectMapper mapper,
            PaymentRepository payments,
            RefundRepository refunds,
            PayoutRepository payouts,
            PayoutItemRepository payoutItems) {
        this.db = db;
        this.mapper = mapper;
        this.payments = payments;
        this.refunds = refunds;
        this.payouts = payouts;
        this.payoutItems = payoutItems;
    }

    public Link existing(long customer, String key, long order) {
        var payment = payments.findByCustomerIdAndIdempotencyKey(customer, key);
        if (payment.isEmpty()) return null;
        if (payment.get().getOrderId() != order)
            throw FinanceException.conflict("IDEMPOTENCY_CONFLICT");
        return link(row(payment.get()));
    }

    @Transactional
    public Link createPayment(
            Intent intent,
            String key,
            String txnRef,
            String url,
            LocalDateTime expires,
            UUID correlation) {
        lock("payment-key:" + intent.customerId() + ":" + key);
        lock("order:" + intent.orderId());
        Link old = existing(intent.customerId(), key, intent.orderId());
        if (old != null) return old;
        if (payments.existsByOrderIdAndStatusIn(intent.orderId(), Set.of("PENDING", "SUCCESS")))
            throw FinanceException.conflict("PAYMENT_ALREADY_FINAL");
        var payment = new PaymentEntity();
        payment.setOrderId(intent.orderId());
        payment.setCustomerId(intent.customerId());
        payment.setAmount(intent.totalAmount());
        payment.setTxnRef(txnRef);
        payment.setIdempotencyKey(key);
        payment.setPaymentUrl(url);
        payment.setExpiresAt(expires);
        payment.setStatus("PENDING");
        payment.setCreatedAt(now());
        payment.setAggregateVersion(1L);
        payments.saveAndFlush(payment);
        var p = row(payment);
        audit(
                "PAYMENT_CREATED",
                "Payment",
                number(p, "id"),
                intent.totalAmount(),
                intent.customerId(),
                correlation);
        return link(p);
    }

    @Transactional
    public Map<String, String> ipn(Callback c, UUID correlation) {
        var found = payments.findByTxnRef(c.txnRef());
        if (found.isEmpty()) return reply("01", "Order not found");
        long order = found.get().getOrderId();
        lock("order:" + order);
        // Reload after acquiring the order lock: another IPN may have committed while this one
        // waited.
        var payment = found.get();
        db.refresh(payment);
        if (payment.getAmount().compareTo(c.amount()) != 0) return reply("04", "Invalid amount");
        if (!"PENDING".equals(payment.getStatus())) return reply("02", "Already confirmed");
        long id = payment.getId();
        var p = row(payment);
        payment.setStatus(c.success() ? "SUCCESS" : "FAILED");
        payment.setGatewayTxnNo(c.gatewayTxnNo());
        payment.setBankCode(c.bankCode());
        payment.setResponseCode(c.responseCode());
        payment.setPaidAt(c.paidAt());
        payment.setAggregateVersion(payment.getAggregateVersion() + 1);
        payments.saveAndFlush(payment);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("paymentId", id);
        payload.put("orderId", order);
        payload.put("txnRef", c.txnRef());
        if (c.success()) {
            payload.put("amount", c.amount());
            payload.put("gatewayTxnNo", c.gatewayTxnNo());
            payload.put("paidAt", c.paidAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
        } else {
            payload.put("responseCode", c.responseCode());
            payload.put("failedAt", Instant.now());
        }
        event(
                c.success() ? "payment.succeeded.v1" : "payment.failed.v1",
                "Payment",
                id,
                number(p, "aggregate_version") + 1,
                payload,
                null,
                correlation,
                null);
        audit(
                c.success() ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED",
                "Payment",
                id,
                c.amount(),
                null,
                correlation);
        if (c.success()) tryRefund(order);
        return reply("00", "Confirm success");
    }

    public Map<String, Object> paymentStatus(long order) {
        return payments.findFirstByOrderIdOrderByIdDesc(order)
                .map(p -> paymentView(p, true))
                .orElseGet(() -> Map.of("orderId", order, "status", "NOT_FOUND"));
    }

    public Map<String, Object> returnStatus(Callback callback) {
        var p =
                payments.findByTxnRef(callback.txnRef())
                        .orElseThrow(
                                () ->
                                        new FinanceException(
                                                404, "NOT_FOUND", "Không tìm thấy giao dịch"));
        if (p.getAmount().compareTo(callback.amount()) != 0)
            throw FinanceException.conflict("PAYMENT_AMOUNT_MISMATCH");
        return paymentView(p, false);
    }

    private Map<String, Object> paymentView(PaymentEntity p, boolean paidAt) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", p.getId());
        view.put("orderId", p.getOrderId());
        view.put("amount", p.getAmount());
        view.put("txnRef", p.getTxnRef());
        view.put("status", p.getStatus());
        if (paidAt)
            view.put(
                    "paidAt",
                    p.getPaidAt() == null
                            ? null
                            : p.getPaidAt()
                                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                                    .toOffsetDateTime());
        return view;
    }

    @Transactional
    public Map<String, Object> completeRefund(
            long id, String reference, long actor, UUID correlation) {
        reference(reference);
        var refund =
                refunds.lockById(id)
                        .orElseThrow(
                                () ->
                                        new FinanceException(
                                                404, "NOT_FOUND", "Không tìm thấy bản ghi"));
        var r = row(refund);
        if ("COMPLETED".equals(r.get("status"))) {
            if (!reference.equals(r.get("gateway_ref")))
                throw FinanceException.conflict("IDEMPOTENCY_CONFLICT");
            return camel(r);
        }
        Instant now = Instant.now();
        refund.setStatus("COMPLETED");
        refund.setGatewayRef(reference);
        refund.setProcessedBy(actor);
        refund.setProcessedAt(LocalDateTime.ofInstant(now, ZoneId.of("Asia/Ho_Chi_Minh")));
        refund.setAggregateVersion(refund.getAggregateVersion() + 1);
        refunds.saveAndFlush(refund);
        event(
                "refund.completed.v1",
                "Refund",
                id,
                number(r, "aggregate_version") + 1,
                Map.of(
                        "refundId",
                        id,
                        "orderId",
                        r.get("order_id"),
                        "amount",
                        r.get("amount"),
                        "gatewayRef",
                        reference,
                        "processedAt",
                        now),
                actor,
                correlation,
                null);
        audit("REFUND_COMPLETED", "Refund", id, money(r, "amount"), actor, correlation);
        return camel(row(refund));
    }

    @Transactional
    public Map<String, Object> createPayout(
            PayoutRequest req, Account account, long actor, String key, UUID correlation) {
        lock("payout-key:" + actor + ":" + key);
        var previous = payouts.findByCreatedByAndIdempotencyKey(actor, key);
        if (!previous.isEmpty()) {
            var p = row(previous.get());
            if (number(p, "store_id") != req.storeId()
                    || !p.get("period_from").toString().equals(req.from().toString())
                    || !p.get("period_to").toString().equals(req.to().toString()))
                throw FinanceException.conflict("IDEMPOTENCY_CONFLICT");
            return safePayout(p);
        }
        lock("payout-store:" + req.storeId());
        var orders =
                db.queryForList(
                        "SELECT s.* FROM order_snapshots s WHERE s.store_id=? AND s.completed_at>=?"
                                + " AND s.completed_at<? AND "
                                + eligible()
                                + " AND NOT EXISTS(SELECT 1 FROM payout_items i WHERE"
                                + " i.order_id=s.order_id AND i.active) ORDER BY s.order_id FOR"
                                + " UPDATE OF s",
                        req.storeId(),
                        req.from().atStartOfDay(),
                        req.to().plusDays(1).atStartOfDay());
        if (orders.isEmpty()) throw FinanceException.conflict("PAYOUT_ORDER_ALREADY_USED");
        BigDecimal gross = BigDecimal.ZERO, fee = BigDecimal.ZERO, net = BigDecimal.ZERO;
        for (var o : orders) {
            gross = gross.add(money(o, "total_amount"));
            fee = fee.add(money(o, "commission_amount"));
            net = net.add(money(o, "store_earning"));
        }
        var payout = new PayoutEntity();
        payout.setPayoutCode(code("PO"));
        payout.setStoreId(req.storeId());
        payout.setPeriodFrom(req.from());
        payout.setPeriodTo(req.to());
        payout.setOrderCount(orders.size());
        payout.setGrossAmount(gross);
        payout.setCommissionAmount(fee);
        payout.setNetAmount(net);
        payout.setBankName(account.bankName());
        payout.setBankAccountNo(account.bankAccountNo());
        payout.setBankAccountName(account.bankAccountName());
        payout.setCreatedBy(actor);
        payout.setIdempotencyKey(key);
        payout.setStatus("PENDING");
        payout.setCreatedAt(now());
        payouts.saveAndFlush(payout);
        long id = payout.getId();
        for (var o : orders) {
            var item = new PayoutItemEntity();
            item.setPayoutId(id);
            item.setOrderId(number(o, "order_id"));
            item.setGrossAmount(money(o, "total_amount"));
            item.setCommissionAmount(money(o, "commission_amount"));
            item.setNetAmount(money(o, "store_earning"));
            item.setActive(true);
            payoutItems.save(item);
        }
        payoutItems.flush();
        audit("PAYOUT_CREATED", "Payout", id, net, actor, correlation);
        return safePayout(row(payout));
    }

    @Transactional
    public Map<String, Object> changePayout(
            long id, String action, String reference, long actor, UUID correlation) {
        if (!Set.of("PAID", "CANCELLED").contains(action))
            throw new FinanceException(400, "VALIDATION_ERROR", "Trạng thái không hợp lệ");
        if ("PAID".equals(action)) reference(reference);
        var payout =
                payouts.lockById(id)
                        .orElseThrow(
                                () ->
                                        new FinanceException(
                                                404, "NOT_FOUND", "Không tìm thấy bản ghi"));
        var p = row(payout);
        if (action.equals(p.get("status"))) {
            if ("PAID".equals(action) && !reference.equals(p.get("transfer_ref")))
                throw FinanceException.conflict("IDEMPOTENCY_CONFLICT");
            return safePayout(p);
        }
        if (!"PENDING".equals(p.get("status")))
            throw FinanceException.conflict("PAYMENT_ALREADY_FINAL");
        payout.setStatus(action);
        if ("PAID".equals(action)) {
            payout.setTransferRef(reference);
            payout.setPaidBy(actor);
            payout.setPaidAt(now());
        } else {
            for (var item : payoutItems.findByPayoutId(id)) {
                item.setActive(false);
                payoutItems.save(item);
            }
            payoutItems.flush();
        }
        payouts.saveAndFlush(payout);
        audit("PAYOUT_" + action, "Payout", id, money(p, "net_amount"), actor, correlation);
        return safePayout(row(payout));
    }

    // Called inside a consumer/IPN/reconciliation transaction, serialized on order ID.
    public void tryRefund(long order) {
        lock("order:" + order);
        var requests =
                db.queryForList("SELECT * FROM finance_refund_requests WHERE order_id=?", order);
        if (requests.isEmpty()) return;
        var r = requests.get(0);
        var payment = payments.findByOrderIdAndStatus(order, "SUCCESS");
        if (payment.isEmpty()) return;
        var p = row(payment.get());
        if (payment.get().getAmount().compareTo(money(r, "amount")) != 0)
            throw FinanceException.conflict("PAYMENT_AMOUNT_MISMATCH");
        if (!db.queryForList(
                        "SELECT order_id FROM order_snapshots WHERE order_id=? AND status IN"
                                + " ('COMPLETED','NO_SHOW')",
                        order)
                .isEmpty()) throw FinanceException.conflict("PAYMENT_INTENT_INVALID");
        if (refunds.existsByOrderId(order)) return;
        var refund = new RefundEntity();
        refund.setRefundCode(code("RF"));
        refund.setOrderId(order);
        refund.setPaymentId(payment.get().getId());
        refund.setAmount(payment.get().getAmount());
        refund.setReason((String) r.get("reason"));
        refund.setRequestedBy((Long) r.get("actor_id"));
        refund.setRequestedAt(now());
        refund.setStatus("PENDING");
        refund.setAggregateVersion(1L);
        refunds.saveAndFlush(refund);
        long id = refund.getId();
        UUID correlation = (UUID) r.get("correlation_id");
        Long actor = (Long) r.get("actor_id");
        event(
                "refund.requested.v1",
                "Refund",
                id,
                1,
                Map.of(
                        "refundId",
                        id,
                        "orderId",
                        order,
                        "amount",
                        p.get("amount"),
                        "reason",
                        r.get("reason"),
                        "requestedAt",
                        Instant.now()),
                actor,
                correlation,
                (UUID) r.get("event_id"));
        audit("REFUND_REQUESTED", "Refund", id, money(p, "amount"), actor, correlation);
    }

    public void event(
            String type,
            String aggregate,
            long id,
            long version,
            Object payload,
            Long actor,
            UUID correlation,
            UUID causation) {
        var e =
                EventEnvelope.create(
                        type,
                        aggregate,
                        Long.toString(id),
                        version,
                        correlation,
                        causation,
                        actor,
                        payload);
        try {
            db.update(
                    """
INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,aggregate_version,event_type,payload,occurred_at)
VALUES (?,?,?,?,?,CAST(? AS jsonb),?)
""",
                    e.eventId(),
                    aggregate,
                    e.aggregateId(),
                    version,
                    type,
                    mapper.writeValueAsString(e),
                    java.sql.Timestamp.from(e.occurredAt()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void audit(
            String action, String type, long id, BigDecimal amount, Long actor, UUID correlation) {
        event(
                "audit.action.v1",
                type,
                id,
                1,
                Map.of("action", action, "entityType", type, "entityId", id, "amount", amount),
                actor,
                correlation,
                null);
    }

    public void lock(String key) {
        db.advisoryLock(key);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    private static Map<String, Object> row(Object entity) {
        var bean = new BeanWrapperImpl(entity);
        Map<String, Object> result = new LinkedHashMap<>();
        for (var property : bean.getPropertyDescriptors()) {
            String name = property.getName();
            if (name.equals("class") || !bean.isReadableProperty(name)) continue;
            result.put(
                    name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT),
                    bean.getPropertyValue(name));
        }
        return result;
    }

    private void reference(String s) {
        if (s == null || s.isBlank() || s.length() > 100)
            throw new FinanceException(
                    400, "VALIDATION_ERROR", "Mã giao dịch bắt buộc, tối đa 100 ký tự");
    }

    private Link link(Map<String, Object> p) {
        return new Link(
                number(p, "id"),
                number(p, "order_id"),
                (String) p.get("txn_ref"),
                (String) p.get("payment_url"),
                (String) p.get("status"));
    }

    private static Map<String, String> reply(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }

    public static String eligible() {
        return """
s.status IN ('COMPLETED','NO_SHOW') AND s.payment_status='PAID'
AND EXISTS(SELECT 1 FROM payments p WHERE p.order_id=s.order_id AND p.status='SUCCESS' AND p.amount=s.total_amount)
AND NOT EXISTS(SELECT 1 FROM refunds r WHERE r.order_id=s.order_id)
AND NOT EXISTS(SELECT 1 FROM finance_refund_requests r WHERE r.order_id=s.order_id)
""";
    }

    public static long number(Map<String, Object> m, String k) {
        return ((Number) m.get(k)).longValue();
    }

    public static BigDecimal money(Map<String, Object> m, String k) {
        return (BigDecimal) m.get(k);
    }

    public static String code(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    public static Map<String, Object> safePayout(Map<String, Object> row) {
        var result = camel(row);
        String account = (String) result.get("bankAccountNo");
        if (account != null)
            result.put(
                    "bankAccountNo", "****" + account.substring(Math.max(0, account.length() - 4)));
        result.remove("idempotencyKey");
        return result;
    }

    public static Map<String, Object> camel(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach(
                (k, v) -> {
                    StringBuilder b = new StringBuilder();
                    boolean upper = false;
                    for (char c : k.toCharArray()) {
                        if (c == '_') {
                            upper = true;
                            continue;
                        }
                        b.append(upper ? Character.toUpperCase(c) : c);
                        upper = false;
                    }
                    if (v instanceof java.sql.Timestamp t)
                        v =
                                t.toLocalDateTime()
                                        .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                                        .toOffsetDateTime();
                    result.put(b.toString(), v);
                });
        return result;
    }
}
