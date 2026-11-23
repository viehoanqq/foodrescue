package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.catalog.domain.model.ReservationRequest;
import com.foodrescue.catalog.domain.model.ReservationResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JdbcReservationAdapter implements com.foodrescue.catalog.application.port.out.IReservationPort {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcReservationAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public ReservationResult reserve(ReservationRequest request) {
        if (request.items().isEmpty() || request.items().size() != new HashSet<>(request.items().stream().map(ReservationRequest.Item::batchId).toList()).size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Batch list must be nonempty and unique");
        }
        List<ReservationRequest.Item> sorted = request.items().stream()
                .sorted(Comparator.comparing(ReservationRequest.Item::batchId)).toList();
        String hash = hash(request.orderId() + ":" + request.storeId() + ":" + request.actorId() + ":" + sorted);
        // Serialize identical reservation IDs even before their row exists.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class,
                request.reservationId().toString());
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT request_hash, status FROM stock_reservations WHERE reservation_id = ? FOR UPDATE", request.reservationId());
        if (!existing.isEmpty()) {
            if (!hash.equals(existing.get(0).get("request_hash"))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation ID used with different request");
            }
            return load(request.reservationId());
        }
        LocalDateTime now = LocalDateTime.now(VIETNAM);
        LocalDateTime pickupStart = null;
        LocalDateTime pickupEnd = null;
        List<ReservationResult.Item> lines = new ArrayList<>();
        for (ReservationRequest.Item item : sorted) {
            if (item.quantity() < 1 || item.quantity() > 5) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be 1-5");
            }
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT store_id, rescue_price, remaining_quantity, pickup_start, pickup_end, status " +
                    "FROM rescue_batches WHERE id = ? FOR UPDATE", item.batchId());
            if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found");
            Map<String, Object> row = rows.get(0);
            LocalDateTime start = ((java.sql.Timestamp) row.get("pickup_start")).toLocalDateTime();
            LocalDateTime end = ((java.sql.Timestamp) row.get("pickup_end")).toLocalDateTime();
            if (!request.storeId().equals(((Number) row.get("store_id")).longValue())
                    || !"AVAILABLE".equals(row.get("status"))
                    || !end.isAfter(now)
                    || ((Number) row.get("remaining_quantity")).intValue() < item.quantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Batch unavailable");
            }
            pickupStart = pickupStart == null || start.isAfter(pickupStart) ? start : pickupStart;
            pickupEnd = pickupEnd == null || end.isBefore(pickupEnd) ? end : pickupEnd;
            lines.add(new ReservationResult.Item(item.batchId(), item.quantity(), (BigDecimal) row.get("rescue_price")));
        }
        if (!pickupEnd.isAfter(pickupStart)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup windows do not overlap");
        }
        Instant expiresAt = Instant.now().plusSeconds(15 * 60);
        jdbc.update("INSERT INTO stock_reservations(reservation_id,order_id,request_hash,status,pickup_start,pickup_end,expires_at) " +
                "VALUES (?,?,?,'HELD',?,?,?)",
                request.reservationId(), request.orderId(), hash, java.sql.Timestamp.valueOf(pickupStart),
                java.sql.Timestamp.valueOf(pickupEnd), java.sql.Timestamp.from(expiresAt));
        for (ReservationResult.Item item : lines) {
            int changed = jdbc.update("UPDATE rescue_batches SET remaining_quantity = remaining_quantity - ?, " +
                    "status = CASE WHEN remaining_quantity - ? = 0 THEN 'SOLD_OUT' ELSE status END " +
                    "WHERE id = ? AND status = 'AVAILABLE' AND remaining_quantity >= ?",
                    item.quantity(), item.quantity(), item.batchId(), item.quantity());
            if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Batch sold out");
            jdbc.update("INSERT INTO stock_reservation_items(reservation_id,batch_id,quantity,unit_price) VALUES (?,?,?,?)",
                    request.reservationId(), item.batchId(), item.quantity(), item.unitPrice());
            jdbc.update("INSERT INTO stock_movements(batch_id,change_qty,reason,order_id,actor_id,remaining_after,note) " +
                    "SELECT id,?,'ORDERED',?,NULL,remaining_quantity,? FROM rescue_batches WHERE id=?",
                    -item.quantity(), request.orderId(), "Reservation " + request.reservationId(), item.batchId());
        }
        writeEvent("stock.reserved.v1", request.reservationId(), 1, request.actorId(), Map.of(
                "reservationId", request.reservationId(), "orderId", request.orderId(),
                "storeId", request.storeId(), "expiresAt", expiresAt.toString(),
                "items", lines.stream().map(line -> Map.of("batchId", line.batchId(),
                        "quantity", line.quantity(), "unitPrice", line.unitPrice())).toList()));
        return new ReservationResult(request.reservationId(), "HELD", lines, pickupStart, pickupEnd);
    }

    @Transactional
    public ReservationResult release(UUID reservationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT order_id, status FROM stock_reservations WHERE reservation_id = ? FOR UPDATE", reservationId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
        String status = (String) rows.get(0).get("status");
        if ("RELEASED".equals(status)) return load(reservationId);
        if (!"HELD".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation cannot be released");
        long orderId = ((Number) rows.get(0).get("order_id")).longValue();
        for (Map<String, Object> item : jdbc.queryForList(
                "SELECT batch_id, quantity FROM stock_reservation_items WHERE reservation_id=? ORDER BY batch_id", reservationId)) {
            long batchId = ((Number) item.get("batch_id")).longValue();
            int qty = ((Number) item.get("quantity")).intValue();
            jdbc.queryForList("SELECT id FROM rescue_batches WHERE id=? FOR UPDATE", batchId);
            jdbc.update("UPDATE rescue_batches SET remaining_quantity=remaining_quantity+?, " +
                    "status=CASE WHEN status='SOLD_OUT' AND pickup_end > ? THEN 'AVAILABLE' ELSE status END WHERE id=?",
                    qty, java.sql.Timestamp.valueOf(LocalDateTime.now(VIETNAM)), batchId);
            jdbc.update("INSERT INTO stock_movements(batch_id,change_qty,reason,order_id,actor_id,remaining_after,note) " +
                    "SELECT id,?,'RETURNED',?,NULL,remaining_quantity,? FROM rescue_batches WHERE id=?",
                    qty, orderId, "Release " + reservationId, batchId);
        }
        jdbc.update("UPDATE stock_reservations SET status='RELEASED' WHERE reservation_id=?", reservationId);
        writeEvent("stock.released.v1", reservationId, 2, null, Map.of(
                "reservationId", reservationId, "orderId", orderId, "reason", "ORDER_RELEASED"));
        return load(reservationId);
    }

    @Transactional(readOnly = true)
    public ReservationResult load(UUID reservationId) {
        Map<String, Object> reservation = jdbc.queryForMap(
                "SELECT status,pickup_start,pickup_end FROM stock_reservations WHERE reservation_id=?", reservationId);
        List<ReservationResult.Item> items = jdbc.query(
                "SELECT batch_id,quantity,unit_price FROM stock_reservation_items WHERE reservation_id=? ORDER BY batch_id",
                (rs, i) -> new ReservationResult.Item(rs.getLong(1), rs.getInt(2), rs.getBigDecimal(3)), reservationId);
        return new ReservationResult(reservationId, (String) reservation.get("status"), items,
                ((java.sql.Timestamp) reservation.get("pickup_start")).toLocalDateTime(),
                ((java.sql.Timestamp) reservation.get("pickup_end")).toLocalDateTime());
    }

    private void writeEvent(String type, UUID reservationId, int version, Long actorId, Map<String, Object> eventPayload) {
        try {
            EventEnvelope<Map<String, Object>> envelope = EventEnvelope.create(type, "StockReservation",
                    reservationId.toString(), version, reservationId, null, actorId, eventPayload);
            String payload = json.writeValueAsString(envelope);
            jdbc.update("INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,aggregate_version,event_type,payload,occurred_at) " +
                    "VALUES (?, 'StockReservation', ?, ?, ?, CAST(? AS jsonb), now())",
                    envelope.eventId(), reservationId.toString(), version, type, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize stock event", e);
        }
    }

    private static String hash(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
