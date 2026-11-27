package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.events.EventEnvelope;
import com.foodrescue.catalog.application.port.out.IReservationPort;
import com.foodrescue.catalog.domain.exception.CatalogException;
import com.foodrescue.catalog.domain.model.ReservationRequest;
import com.foodrescue.catalog.domain.model.ReservationResult;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.OutboxEventEntity;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.RescueBatchEntity;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.StockMovementEntity;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.StockReservationEntity;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.StockReservationItemEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaReservationAdapter implements IReservationPort {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ISpringDataRescueBatchRepository batches;
    private final ISpringDataStockReservationRepository reservations;
    private final ISpringDataStockReservationItemRepository reservationItems;
    private final ObjectMapper json;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaReservationAdapter(ISpringDataRescueBatchRepository batches,
            ISpringDataStockReservationRepository reservations,
            ISpringDataStockReservationItemRepository reservationItems, ObjectMapper json) {
        this.batches = batches;
        this.reservations = reservations;
        this.reservationItems = reservationItems;
        this.json = json;
    }

    @Override
    @Transactional
    public ReservationResult reserve(ReservationRequest request) {
        if (request.items().isEmpty()
                || request.items().size() != new HashSet<>(request.items().stream()
                        .map(ReservationRequest.Item::batchId).toList()).size()) {
            throw CatalogException.badRequest("VALIDATION_ERROR", "Batch list must be nonempty and unique");
        }
        entityManager.createNativeQuery(
                "SELECT pg_advisory_xact_lock(hashtextextended(:reservationId, 0))")
                .setParameter("reservationId", request.reservationId().toString()).getResultList();
        String requestHash = hash(request.orderId() + ":" + request.storeId() + ":"
                + request.actorId() + ":" + request.items().stream()
                        .sorted(Comparator.comparing(ReservationRequest.Item::batchId)).toList());
        var existing = reservations.findForUpdateByReservationId(request.reservationId());
        if (existing.isPresent()) {
            if (!requestHash.equals(existing.get().getRequestHash())) {
                throw CatalogException.conflict("RESERVATION_CONFLICT",
                        "Reservation ID used with a different request");
            }
            return load(request.reservationId());
        }

        LocalDateTime now = LocalDateTime.now(VIETNAM);
        LocalDateTime pickupStart = null;
        LocalDateTime pickupEnd = null;
        List<ReservationResult.Item> resultItems = new ArrayList<>();
        List<RescueBatchEntity> lockedBatches = new ArrayList<>();
        List<ReservationRequest.Item> sortedItems = request.items().stream()
                .sorted(Comparator.comparing(ReservationRequest.Item::batchId)).toList();
        for (ReservationRequest.Item item : sortedItems) {
            RescueBatchEntity batch = batches.findForUpdateByIdAndDeletedAtIsNull(item.batchId())
                    .orElseThrow(() -> CatalogException.notFound("BATCH_NOT_FOUND", "Batch not found"));
            if (!request.storeId().equals(batch.getStoreId())
                    || !"AVAILABLE".equals(batch.getStatus())
                    || !batch.getPickupEnd().isAfter(now)
                    || batch.getRemainingQuantity() < item.quantity()) {
                throw CatalogException.conflict("BATCH_NOT_AVAILABLE", "Batch unavailable");
            }
            if (pickupStart == null || batch.getPickupStart().isAfter(pickupStart)) {
                pickupStart = batch.getPickupStart();
            }
            if (pickupEnd == null || batch.getPickupEnd().isBefore(pickupEnd)) {
                pickupEnd = batch.getPickupEnd();
            }
            resultItems.add(new ReservationResult.Item(item.batchId(), item.quantity(), batch.getRescuePrice()));
            lockedBatches.add(batch);
        }
        if (pickupStart == null || pickupEnd == null || !pickupEnd.isAfter(pickupStart)) {
            throw CatalogException.conflict("BATCH_NOT_AVAILABLE", "Pickup windows do not overlap");
        }

        OffsetDateTime expiresAt = OffsetDateTime.now(VIETNAM).plusMinutes(15);
        StockReservationEntity reservation = new StockReservationEntity();
        reservation.setReservationId(request.reservationId());
        reservation.setOrderId(request.orderId());
        reservation.setRequestHash(requestHash);
        reservation.setStatus("HELD");
        reservation.setPickupStart(pickupStart);
        reservation.setPickupEnd(pickupEnd);
        reservation.setExpiresAt(expiresAt);
        reservation.setCreatedAt(OffsetDateTime.now(VIETNAM));
        reservations.saveAndFlush(reservation);

        for (int i = 0; i < lockedBatches.size(); i++) {
            RescueBatchEntity batch = lockedBatches.get(i);
            ReservationResult.Item line = resultItems.get(i);
            int remaining = batch.getRemainingQuantity() - line.quantity();
            batch.setRemainingQuantity(remaining);
            if (remaining == 0) batch.setStatus("SOLD_OUT");
            batch.setAggregateVersion(batch.getAggregateVersion() + 1);
            batches.saveAndFlush(batch);

            StockReservationItemEntity item = new StockReservationItemEntity();
            item.setReservationId(request.reservationId());
            item.setBatchId(line.batchId());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.unitPrice());
            reservationItems.save(item);
            appendMovement(line.batchId(), -line.quantity(), "ORDERED", request.orderId(), null,
                    remaining, "Reservation " + request.reservationId(), now);
        }
        appendEvent("stock.reserved.v1", request.reservationId(), 1, request.actorId(), Map.of(
                "reservationId", request.reservationId(), "orderId", request.orderId(),
                "storeId", request.storeId(), "expiresAt", expiresAt.toInstant().toString(),
                "items", resultItems.stream().map(line -> Map.of("batchId", line.batchId(),
                        "quantity", line.quantity(), "unitPrice", line.unitPrice())).toList()));
        return new ReservationResult(request.reservationId(), "HELD", resultItems, pickupStart, pickupEnd);
    }

    @Override
    @Transactional
    public ReservationResult release(UUID reservationId) {
        StockReservationEntity reservation = reservations.findForUpdateByReservationId(reservationId)
                .orElseThrow(() -> CatalogException.notFound("RESERVATION_NOT_FOUND", "Reservation not found"));
        if ("RELEASED".equals(reservation.getStatus())) return load(reservationId);
        if (!"HELD".equals(reservation.getStatus())) {
            throw CatalogException.conflict("RESERVATION_CONFLICT", "Reservation cannot be released");
        }
        LocalDateTime now = LocalDateTime.now(VIETNAM);
        for (StockReservationItemEntity item : reservationItems.findByReservationIdOrderByBatchId(reservationId)) {
            RescueBatchEntity batch = batches.findForUpdateByIdAndDeletedAtIsNull(item.getBatchId())
                    .orElseThrow(() -> CatalogException.conflict(
                            "RESERVATION_CONFLICT", "A reserved batch is no longer available"));
            int remaining = batch.getRemainingQuantity() + item.getQuantity();
            batch.setRemainingQuantity(remaining);
            if ("SOLD_OUT".equals(batch.getStatus()) && batch.getPickupEnd().isAfter(now)) {
                batch.setStatus("AVAILABLE");
            }
            batch.setAggregateVersion(batch.getAggregateVersion() + 1);
            batches.saveAndFlush(batch);
            appendMovement(batch.getId(), item.getQuantity(), "RETURNED", reservation.getOrderId(),
                    null, remaining, "Release " + reservationId, now);
        }
        reservation.setStatus("RELEASED");
        reservations.saveAndFlush(reservation);
        appendEvent("stock.released.v1", reservationId, 2, null,
                Map.of("reservationId", reservationId, "orderId", reservation.getOrderId(),
                        "reason", "ORDER_RELEASED"));
        return load(reservationId);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResult load(UUID reservationId) {
        StockReservationEntity reservation = reservations.findById(reservationId)
                .orElseThrow(() -> CatalogException.notFound("RESERVATION_NOT_FOUND", "Reservation not found"));
        List<ReservationResult.Item> items = reservationItems.findByReservationIdOrderByBatchId(reservationId)
                .stream().map(item -> new ReservationResult.Item(item.getBatchId(), item.getQuantity(),
                        item.getUnitPrice())).toList();
        return new ReservationResult(reservationId, reservation.getStatus(), items,
                reservation.getPickupStart(), reservation.getPickupEnd());
    }

    private void appendMovement(Long batchId, int change, String reason, Long orderId, Long actorId,
                                int remaining, String note, LocalDateTime createdAt) {
        StockMovementEntity movement = new StockMovementEntity();
        movement.setBatchId(batchId);
        movement.setChangeQty(change);
        movement.setReason(reason);
        movement.setOrderId(orderId);
        movement.setActorId(actorId);
        movement.setRemainingAfter(remaining);
        movement.setNote(note);
        movement.setCreatedAt(createdAt);
        entityManager.persist(movement);
    }

    private void appendEvent(String type, UUID aggregateId, long version, Long actorId,
                             Map<String, Object> payload) {
        try {
            EventEnvelope<Map<String, Object>> event = EventEnvelope.create(type, "StockReservation",
                    aggregateId.toString(), version, aggregateId, null, actorId, payload);
            com.fasterxml.jackson.databind.JsonNode serialized = json.readTree(json.writeValueAsString(event));
            entityManager.persist(new OutboxEventEntity(event.eventId(), event.aggregateType(),
                    event.aggregateId(), event.aggregateVersion(), event.eventType(), event.schemaVersion(),
                    serialized, event.occurredAt()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize stock event", ex);
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
