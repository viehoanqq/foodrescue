package com.foodrescue.order.application.usecase;

import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataPendingReleaseRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.PendingInventoryReleaseEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryReleaseService {
    private static final Logger log = LoggerFactory.getLogger(InventoryReleaseService.class);

    private final IInventoryPort inventoryPort;
    private final SpringDataPendingReleaseRepository pendingReleaseRepository;

    public InventoryReleaseService(
            IInventoryPort inventoryPort,
            SpringDataPendingReleaseRepository pendingReleaseRepository) {
        this.inventoryPort = inventoryPort;
        this.pendingReleaseRepository = pendingReleaseRepository;
    }

    public void releaseOrEnqueue(UUID reservationId, String reason, UUID correlationId) {
        if (reservationId == null) {
            return;
        }
        UUID corr = correlationId != null ? correlationId : UUID.randomUUID();
        try {
            inventoryPort.release(reservationId, corr);
            pendingReleaseRepository.findByReservationId(reservationId.toString())
                    .ifPresent(pendingReleaseRepository::delete);
        } catch (Exception ex) {
            log.warn("Inventory release failed for reservationId={}, enqueueing retry: {}", reservationId, ex.getMessage());
            enqueueRetry(reservationId, reason, ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueRetry(UUID reservationId, String reason, String errorMessage) {
        String resKey = reservationId.toString();
        LocalDateTime now = LocalDateTime.now();
        PendingInventoryReleaseEntity entity = pendingReleaseRepository.findByReservationId(resKey)
                .orElseGet(() -> {
                    PendingInventoryReleaseEntity e = new PendingInventoryReleaseEntity();
                    e.setReservationId(resKey);
                    e.setReason(reason);
                    e.setCreatedAt(now);
                    return e;
                });
        entity.setAttempts(entity.getAttempts() + 1);
        entity.setNextAttemptAt(now.plusSeconds(30));
        entity.setLastError(truncate(errorMessage, 500));
        pendingReleaseRepository.save(entity);
    }

    public void retryPendingReleases() {
        List<PendingInventoryReleaseEntity> dueList = pendingReleaseRepository.findDueForRetry(LocalDateTime.now());
        for (PendingInventoryReleaseEntity item : dueList) {
            try {
                inventoryPort.release(UUID.fromString(item.getReservationId()), UUID.randomUUID());
                pendingReleaseRepository.delete(item);
                log.info("Retry inventory release succeeded for reservationId={}", item.getReservationId());
            } catch (Exception ex) {
                int attempts = item.getAttempts() + 1;
                long backoffSeconds = Math.min(3600L, 30L * (1L << Math.min(attempts, 6)));
                item.setAttempts(attempts);
                item.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSeconds));
                item.setLastError(truncate(ex.getMessage(), 500));
                pendingReleaseRepository.save(item);
                log.warn("Retry inventory release failed (attempt {}) for reservationId={}: {}",
                        attempts, item.getReservationId(), ex.getMessage());
            }
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
