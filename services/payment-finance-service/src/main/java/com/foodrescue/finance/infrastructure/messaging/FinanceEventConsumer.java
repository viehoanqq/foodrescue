package com.foodrescue.finance.infrastructure.messaging;

import com.fasterxml.jackson.databind.*;
import com.foodrescue.finance.infrastructure.adapter.out.persistence.JpaFinanceLedger;
import com.foodrescue.finance.infrastructure.adapter.out.persistence.JpaFinanceQueries;
import com.foodrescue.finance.infrastructure.adapter.out.persistence.OrderSnapshotRepository;
import com.foodrescue.finance.infrastructure.adapter.out.persistence.entity.OrderSnapshotEntity;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Component
public class FinanceEventConsumer {
    private final JpaFinanceQueries db;
    private final ObjectMapper mapper;
    private final JpaFinanceLedger ledger;

    private final OrderSnapshotRepository snapshots;

    public FinanceEventConsumer(
            JpaFinanceQueries db,
            ObjectMapper mapper,
            JpaFinanceLedger ledger,
            OrderSnapshotRepository snapshots) {
        this.db = db;
        this.mapper = mapper;
        this.ledger = ledger;
        this.snapshots = snapshots;
    }

    @RabbitListener(queues = "finance.order-snapshots")
    @Transactional
    public void snapshot(String body) throws Exception {
        JsonNode e = validate(body, "order.state-changed.v1"), p = e.get("payload");
        exact(
                p,
                "orderId",
                "storeId",
                "customerId",
                "status",
                "paymentStatus",
                "totalAmount",
                "commissionAmount",
                "storeEarning",
                "completedAt",
                "reservationId",
                "items");
        long order = positive(p, "orderId"), store = positive(p, "storeId");
        positive(p, "customerId");
        String status =
                choice(
                        p,
                        "status",
                        Set.of(
                                "CREATING",
                                "PENDING_PAYMENT",
                                "CONFIRMED",
                                "COMPLETED",
                                "NO_SHOW",
                                "EXPIRED",
                                "CANCELLED"));
        String pay =
                choice(p, "paymentStatus", Set.of("UNPAID", "PAID", "REFUND_PENDING", "REFUNDED"));
        BigDecimal total = amount(p, "totalAmount", 1),
                fee = amount(p, "commissionAmount", 0),
                net = amount(p, "storeEarning", 0);
        if (total.compareTo(fee.add(net)) != 0)
            throw new IllegalArgumentException("Invalid snapshot totals");
        LocalDateTime completed = null;
        if (!p.get("completedAt").isNull())
            completed =
                    OffsetDateTime.parse(p.get("completedAt").asText())
                            .atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh"))
                            .toLocalDateTime();
        if (Set.of("COMPLETED", "NO_SHOW").contains(status) && completed == null)
            throw new IllegalArgumentException("Missing completion time");
        if (!p.get("reservationId").isNull()) UUID.fromString(p.get("reservationId").asText());
        if (!p.get("items").isArray()) throw new IllegalArgumentException("Invalid items");
        for (JsonNode item : p.get("items")) {
            exact(item, "batchId", "quantity");
            positive(item, "batchId");
            if (positive(item, "quantity") > 5)
                throw new IllegalArgumentException("Invalid quantity");
        }
        aggregate(e, order);
        ledger.lock("order:" + order);
        if (!inbox("finance.order-snapshots", e)) return;
        var snapshot = snapshots.findById(order).orElseGet(OrderSnapshotEntity::new);
        long version = e.get("aggregateVersion").longValue();
        if (snapshot.getAggregateVersion() != null && snapshot.getAggregateVersion() >= version)
            return;
        snapshot.setOrderId(order);
        snapshot.setStoreId(store);
        snapshot.setStatus(status);
        snapshot.setPaymentStatus(pay);
        snapshot.setTotalAmount(total);
        snapshot.setCommissionAmount(fee);
        snapshot.setStoreEarning(net);
        snapshot.setCompletedAt(completed);
        snapshot.setAggregateVersion(version);
        snapshot.setLastEventId(UUID.fromString(e.get("eventId").asText()));
        snapshot.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        snapshots.saveAndFlush(snapshot);
    }

    @RabbitListener(queues = "finance.refund-required")
    @Transactional
    public void refund(String body) throws Exception {
        JsonNode e = validate(body, "order.refund-required.v1"), p = e.get("payload");
        exact(p, "orderId", "amount", "reason", "cancelledAt");
        long order = positive(p, "orderId");
        aggregate(e, order);
        BigDecimal amount = amount(p, "amount", 1);
        String reason =
                choice(
                        p,
                        "reason",
                        Set.of(
                                "CUSTOMER_CANCELLED",
                                "STORE_CANCELLED",
                                "BATCH_CANCELLED",
                                "ADMIN_CANCELLED",
                                "LATE_PAYMENT"));
        if (!p.get("cancelledAt").isNull()) OffsetDateTime.parse(p.get("cancelledAt").asText());
        ledger.lock("order:" + order);
        if (!inbox("finance.refund-required", e)) return;
        db.update(
                """
INSERT INTO finance_refund_requests(order_id,amount,reason,event_id,correlation_id,actor_id)
VALUES (?,?,?,?,?,?) ON CONFLICT(order_id) DO NOTHING
""",
                order,
                amount,
                reason,
                UUID.fromString(e.get("eventId").asText()),
                UUID.fromString(e.get("correlationId").asText()),
                e.get("actorId").isNull() ? null : e.get("actorId").longValue());
        BigDecimal saved =
                db.queryForObject(
                        "SELECT amount FROM finance_refund_requests WHERE order_id=?",
                        BigDecimal.class,
                        order);
        if (saved.compareTo(amount) != 0)
            throw new IllegalArgumentException("Conflicting refund amount");
        ledger.tryRefund(order);
    }

    private boolean inbox(String consumer, JsonNode e) {
        return db.update(
                        "INSERT INTO inbox_events(consumer_name,event_id,processed_at) VALUES"
                                + " (?,?,now()) ON CONFLICT DO NOTHING",
                        consumer,
                        UUID.fromString(e.get("eventId").asText()))
                == 1;
    }

    private JsonNode validate(String body, String type) throws Exception {
        JsonNode e = mapper.readTree(body);
        exact(
                e,
                "eventId",
                "eventType",
                "schemaVersion",
                "aggregateType",
                "aggregateId",
                "aggregateVersion",
                "occurredAt",
                "correlationId",
                "causationId",
                "actorId",
                "payload");
        if (!type.equals(e.get("eventType").asText())
                || !e.get("schemaVersion").isIntegralNumber()
                || e.get("schemaVersion").asInt() != 1
                || !"Order".equals(e.get("aggregateType").asText()))
            throw new IllegalArgumentException("Invalid event type/version");
        positive(e, "aggregateVersion");
        UUID.fromString(e.get("eventId").asText());
        UUID.fromString(e.get("correlationId").asText());
        Instant.parse(e.get("occurredAt").asText());
        if (!e.get("causationId").isNull()) UUID.fromString(e.get("causationId").asText());
        if (!e.get("actorId").isNull()) positive(e, "actorId");
        return e;
    }

    private void aggregate(JsonNode e, long order) {
        if (!Long.toString(order).equals(e.get("aggregateId").asText()))
            throw new IllegalArgumentException("Aggregate mismatch");
    }

    private static void exact(JsonNode n, String... names) {
        if (n == null || !n.isObject() || n.size() != names.length)
            throw new IllegalArgumentException("Invalid object fields");
        for (String name : names)
            if (!n.has(name)) throw new IllegalArgumentException("Missing field: " + name);
    }

    private static long positive(JsonNode n, String key) {
        JsonNode v = n.get(key);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToLong() || v.longValue() < 1)
            throw new IllegalArgumentException("Invalid " + key);
        return v.longValue();
    }

    private static BigDecimal amount(JsonNode n, String key, int min) {
        JsonNode v = n.get(key);
        if (v == null
                || !v.isNumber()
                || v.decimalValue().stripTrailingZeros().scale() > 0
                || v.decimalValue().compareTo(BigDecimal.valueOf(min)) < 0)
            throw new IllegalArgumentException("Invalid " + key);
        return v.decimalValue();
    }

    private static String choice(JsonNode n, String key, Set<String> choices) {
        String v = n.get(key).asText();
        if (!choices.contains(v)) throw new IllegalArgumentException("Invalid " + key);
        return v;
    }
}
