package com.foodrescue.finance;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.finance.domain.model.FinanceModels.*;
import com.foodrescue.finance.infrastructure.adapter.out.persistence.*;
import com.foodrescue.finance.infrastructure.messaging.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DB_URL", matches = ".+")
class FinanceDatabaseTest {
    JdbcTemplate db;
    org.springframework.context.annotation.AnnotationConfigApplicationContext context;
    com.foodrescue.finance.infrastructure.adapter.out.persistence.JpaFinanceQueries queries;
    JpaFinanceLedger ledger;
    FinanceEventConsumer consumer;
    TransactionTemplate tx;
    ObjectMapper mapper =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(
                            com.fasterxml.jackson.databind.SerializationFeature
                                    .WRITE_DATES_AS_TIMESTAMPS);
    UUID correlation = UUID.randomUUID();

    @BeforeEach
    void setup() {
        assertTrue(
                System.getenv("FINANCE_TEST_DB_URL").endsWith("/finance_test"),
                "Use an isolated finance_test database");
        var source =
                new DriverManagerDataSource(
                        System.getenv("FINANCE_TEST_DB_URL"), "postgres", "finance_test");
        Flyway.configure().dataSource(source).load().migrate();
        db = new JdbcTemplate(source);
        context =
                new org.springframework.context.annotation.AnnotationConfigApplicationContext(
                        JpaTestDatabase.class);
        queries =
                context.getBean(
                        com.foodrescue.finance.infrastructure.adapter.out.persistence
                                .JpaFinanceQueries.class);

        tx =
                new TransactionTemplate(
                        context.getBean(
                                org.springframework.transaction.PlatformTransactionManager.class));
        db.execute(
                "TRUNCATE"
                    + " payout_items,payouts,refunds,payments,order_snapshots,inbox_events,outbox_events,finance_refund_requests"
                    + " RESTART IDENTITY CASCADE");
        ledger = context.getBean(JpaFinanceLedger.class);
        consumer =
                new FinanceEventConsumer(
                        queries, mapper, ledger, context.getBean(OrderSnapshotRepository.class));
    }

    @AfterEach
    void closeJpa() {
        if (context != null) context.close();
    }

    Link payment(long order) {
        return tx.execute(
                t ->
                        ledger.createPayment(
                                new Intent(
                                        order,
                                        "FR" + order,
                                        8L,
                                        2L,
                                        new BigDecimal("45000"),
                                        "PENDING_PAYMENT",
                                        "UNPAID",
                                        LocalDateTime.now().plusMinutes(15)),
                                "key" + order,
                                "ref" + order,
                                "https://sandbox.test",
                                LocalDateTime.now().plusMinutes(10),
                                correlation));
    }

    Map<String, String> success(long order) {
        return tx.execute(
                t ->
                        ledger.ipn(
                                new Callback(
                                        "ref" + order,
                                        new BigDecimal("45000"),
                                        "00",
                                        "00",
                                        "123",
                                        "NCB",
                                        LocalDateTime.of(2026, 11, 27, 7, 0)),
                                correlation));
    }

    void snapshot(long order, long version, String status) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("orderId", order);
        p.put("storeId", 2);
        p.put("customerId", 8);
        p.put("status", status);
        p.put("paymentStatus", "PAID");
        p.put("totalAmount", 45000);
        p.put("commissionAmount", 6750);
        p.put("storeEarning", 38250);
        p.put(
                "completedAt",
                Set.of("COMPLETED", "NO_SHOW").contains(status) ? "2026-11-27T00:00:00Z" : null);
        p.put("reservationId", null);
        p.put("items", List.of(Map.of("batchId", 1, "quantity", 1)));
        String event =
                json(
                        EventEnvelope.create(
                                "order.state-changed.v1",
                                "Order",
                                "" + order,
                                version,
                                correlation,
                                null,
                                null,
                                p));
        tx.executeWithoutResult(
                t -> {
                    try {
                        consumer.snapshot(event);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    long count(String table) {
        return db.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    @Test
    void ipnIsIdempotentAndAmountChecked() {
        payment(1);
        assertEquals(
                "04",
                tx.execute(
                                t ->
                                        ledger.ipn(
                                                new Callback(
                                                        "ref1",
                                                        BigDecimal.ONE,
                                                        "00",
                                                        "00",
                                                        "123",
                                                        "NCB",
                                                        LocalDateTime.now()),
                                                correlation))
                        .get("RspCode"));
        assertEquals("00", success(1).get("RspCode"));
        long events = count("outbox_events");
        assertEquals("02", success(1).get("RspCode"));
        assertEquals(events, count("outbox_events"));
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT count(*) FROM outbox_events WHERE"
                                + " event_type='payment.succeeded.v1'",
                        Integer.class));
    }

    @Test
    void returnNeverMutatesPayment() {
        payment(1);
        ledger.returnStatus(
                new Callback(
                        "ref1",
                        new BigDecimal("45000"),
                        "00",
                        "00",
                        "123",
                        "NCB",
                        LocalDateTime.now()));
        assertEquals("PENDING", ledger.paymentStatus(1).get("status"));
    }

    @Test
    void businessAndOutboxRollbackTogether() {
        assertThrows(
                RuntimeException.class,
                () ->
                        tx.executeWithoutResult(
                                t -> {
                                    payment(1);
                                    throw new RuntimeException("rollback");
                                }));
        assertEquals(0, count("payments"));
        assertEquals(0, count("outbox_events"));
    }

    @Test
    void snapshotDoesNotRegress() {
        snapshot(1, 3, "COMPLETED");
        snapshot(1, 2, "CONFIRMED");
        assertEquals(
                "COMPLETED",
                db.queryForObject(
                        "SELECT status FROM order_snapshots WHERE order_id=1", String.class));
    }

    @Test
    void refundBeforeIpnIsRetainedAndDeduplicated() {
        payment(1);
        var p =
                new LinkedHashMap<String, Object>(
                        Map.of("orderId", 1, "amount", 45000, "reason", "LATE_PAYMENT"));
        p.put("cancelledAt", null);
        String e =
                json(
                        EventEnvelope.create(
                                "order.refund-required.v1",
                                "Order",
                                "1",
                                2,
                                correlation,
                                null,
                                null,
                                p));
        for (int i = 0; i < 2; i++)
            tx.executeWithoutResult(
                    t -> {
                        try {
                            consumer.refund(e);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        assertEquals(1, count("finance_refund_requests"));
        assertEquals(0, count("refunds"));
        success(1);
        assertEquals(1, count("refunds"));
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT count(*) FROM outbox_events WHERE event_type='refund.requested.v1'",
                        Integer.class));
        tx.executeWithoutResult(t -> ledger.completeRefund(1, "bank-ref", 3, correlation));
        long events = count("outbox_events");
        tx.executeWithoutResult(t -> ledger.completeRefund(1, "bank-ref", 3, correlation));
        assertEquals(events, count("outbox_events"));
    }

    @Test
    void concurrentPayoutsCannotReuseOrders() throws Exception {
        payment(1);
        success(1);
        snapshot(1, 3, "COMPLETED");
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> create =
                    () -> {
                        try {
                            tx.execute(
                                    t ->
                                            ledger.createPayout(
                                                    new PayoutRequest(
                                                            2,
                                                            LocalDate.of(2026, 11, 27),
                                                            LocalDate.of(2026, 11, 27)),
                                                    new Account(
                                                            2L,
                                                            "APPROVED",
                                                            "bank",
                                                            "123456789",
                                                            "Owner"),
                                                    3,
                                                    UUID.randomUUID().toString(),
                                                    correlation));
                            return true;
                        } catch (RuntimeException e) {
                            return false;
                        }
                    };
            var results = executor.invokeAll(List.of(create, create));
            assertEquals(
                    1,
                    results.stream()
                            .filter(
                                    f -> {
                                        try {
                                            return f.get();
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            .count());
            assertEquals(1, count("payout_items"));
        } finally {
            executor.shutdownNow();
        }
        var report = new JpaFinanceReports(queries);
        assertEquals(BigDecimal.ZERO, report.reconciliation().get("difference"));
        tx.executeWithoutResult(t -> ledger.changePayout(1, "CANCELLED", null, 3, correlation));
        assertEquals(
                0,
                db.queryForObject("SELECT count(*) FROM payout_items WHERE active", Integer.class));
    }

    @Test
    void reconciliationRepairsMissingRefundEvent() {
        payment(1);
        success(1);
        snapshot(1, 3, "EXPIRED");
        new RefundReconciliation(queries, ledger, tx.getTransactionManager()).reconcile();
        assertEquals(1, count("refunds"));
    }

    @Test
    void upgradePreservesExistingPayment() {
        var source =
                new DriverManagerDataSource(
                        System.getenv("FINANCE_TEST_DB_URL"), "postgres", "finance_test");
        db.execute("CREATE SCHEMA finance_upgrade_test");
        try {
            Flyway.configure()
                    .dataSource(source)
                    .schemas("finance_upgrade_test")
                    .defaultSchema("finance_upgrade_test")
                    .target("1")
                    .load()
                    .migrate();
            db.update(
                    "INSERT INTO finance_upgrade_test.payments(order_id,amount,txn_ref) VALUES"
                            + " (1,45000,'legacy')");
            Flyway.configure()
                    .dataSource(source)
                    .schemas("finance_upgrade_test")
                    .defaultSchema("finance_upgrade_test")
                    .load()
                    .migrate();
            assertEquals(
                    1,
                    db.queryForObject(
                            "SELECT count(*) FROM finance_upgrade_test.payments WHERE"
                                    + " txn_ref='legacy' AND aggregate_version=1",
                            Integer.class));
        } finally {
            db.execute("DROP SCHEMA finance_upgrade_test CASCADE");
        }
    }

    @Test
    void paidPayoutCannotBeCancelled() {
        payment(1);
        success(1);
        snapshot(1, 3, "COMPLETED");
        tx.execute(
                t ->
                        ledger.createPayout(
                                new PayoutRequest(
                                        2, LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 27)),
                                new Account(2L, "APPROVED", "bank", "123456789", "Owner"),
                                3,
                                "payout-key",
                                correlation));
        tx.executeWithoutResult(t -> ledger.changePayout(1, "PAID", "transfer-1", 3, correlation));
        assertThrows(
                com.foodrescue.finance.domain.exception.FinanceException.class,
                () ->
                        tx.executeWithoutResult(
                                t -> ledger.changePayout(1, "CANCELLED", null, 3, correlation)));
        assertEquals(
                1,
                db.queryForObject("SELECT count(*) FROM payout_items WHERE active", Integer.class));
    }

    @Test
    void concurrentPaymentRequestsReuseSavedLink() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures =
                    executor.invokeAll(List.<Callable<Link>>of(() -> payment(1), () -> payment(1)));
            assertEquals(futures.get(0).get(), futures.get(1).get());
            assertEquals(1, count("payments"));
        } finally {
            executor.shutdownNow();
        }
    }
}
