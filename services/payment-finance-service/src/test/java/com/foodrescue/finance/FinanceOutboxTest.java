package com.foodrescue.finance;

import static org.junit.jupiter.api.Assertions.*;

import com.foodrescue.finance.infrastructure.messaging.outbox.OutboxPublisher;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.*;
import java.util.concurrent.*;

@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_RABBIT_PORT", matches = ".+")
class FinanceOutboxTest {
    JdbcTemplate db;
    org.springframework.context.annotation.AnnotationConfigApplicationContext context;
    com.foodrescue.finance.infrastructure.adapter.out.persistence.JpaFinanceQueries queries;
    CachingConnectionFactory connection;
    RabbitTemplate rabbit;
    RabbitAdmin admin;
    String queue = "finance-test-outbox";

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

        db.execute("TRUNCATE outbox_events");
        connection =
                new CachingConnectionFactory(
                        "localhost", Integer.parseInt(System.getenv("FINANCE_TEST_RABBIT_PORT")));
        connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connection.setPublisherReturns(true);
        rabbit = new RabbitTemplate(connection);
        rabbit.setReceiveTimeout(2000);
        admin = new RabbitAdmin(connection);
        var exchange = new TopicExchange("foodrescue.events", true, false);
        var q = new org.springframework.amqp.core.Queue(queue, true, false, false);
        admin.declareExchange(exchange);
        admin.declareQueue(q);
        admin.declareBinding(BindingBuilder.bind(q).to(exchange).with("payment.succeeded.v1"));
        admin.purgeQueue(queue);
    }

    @AfterEach
    void close() {
        admin.deleteQueue(queue);
        connection.destroy();
        context.close();
    }

    UUID event(String type) {
        UUID id = UUID.randomUUID();
        db.update(
                "INSERT INTO"
                    + " outbox_events(event_id,aggregate_type,aggregate_id,aggregate_version,event_type,payload,occurred_at)"
                    + " VALUES (?,'Payment','1',1,?,CAST(? AS jsonb),now())",
                id,
                type,
                "{\"eventId\":\"" + id + "\"}");
        return id;
    }

    @Test
    void confirmsPersistentJsonAndConcurrentPublishersClaimOnce() throws Exception {
        UUID id = event("payment.succeeded.v1");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var publisher = new OutboxPublisher(queries, rabbit);
            var tasks =
                    executor.invokeAll(
                            List.of(
                                    Executors.callable(publisher::publish),
                                    Executors.callable(publisher::publish)));
            for (var task : tasks) task.get();
        } finally {
            executor.shutdownNow();
        }
        var message = rabbit.receive(queue);
        assertNotNull(message);
        assertEquals(id.toString(), message.getMessageProperties().getMessageId());
        assertEquals("application/json", message.getMessageProperties().getContentType());
        assertEquals(
                MessageDeliveryMode.PERSISTENT,
                message.getMessageProperties().getReceivedDeliveryMode());
        assertNull(rabbit.receive(queue));
        assertNotNull(
                db.queryForObject(
                        "SELECT published_at FROM outbox_events WHERE event_id=?",
                        java.sql.Timestamp.class,
                        id));
    }

    @Test
    void unroutableMessageRemainsPendingWithRetry() {
        UUID id = event("test.unroutable.v1");
        new OutboxPublisher(queries, rabbit).publish();
        var row = db.queryForMap("SELECT * FROM outbox_events WHERE event_id=?", id);
        assertNull(row.get("published_at"));
        assertNotNull(row.get("next_attempt_at"));
        assertEquals(1, ((Number) row.get("attempts")).intValue());
    }
}
