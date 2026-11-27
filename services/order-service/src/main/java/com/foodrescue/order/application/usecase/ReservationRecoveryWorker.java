package com.foodrescue.order.application.usecase;

import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IReservationRecoveryPort;
import com.foodrescue.order.domain.model.StockReservation;
import com.foodrescue.order.domain.model.enums.ReservationStatus;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReservationRecoveryWorker {
    private static final Logger log = LoggerFactory.getLogger(ReservationRecoveryWorker.class);
    private final IReservationRecoveryPort recovery;
    private final SpringDataOrderRepository orders;
    private final IInventoryPort inventory;

    public ReservationRecoveryWorker(IReservationRecoveryPort recovery, SpringDataOrderRepository orders,
                                     IInventoryPort inventory) {
        this.recovery = recovery;
        this.orders = orders;
        this.inventory = inventory;
    }

    @Scheduled(fixedDelayString = "${reservation.recovery.poll-ms:15000}")
    public void recoverPending() {
        for (UUID id : recovery.findDue(50)) recoverOne(id);
    }

    public void recoverOne(UUID id) {
        if (!recovery.tryClaim(id)) return;
        try {
            var order = orders.findByReservationId(id);
            if (order.isPresent()) {
                recovery.markOrderFound(id, order.get().getId());
                return;
            }
            StockReservation current = inventory.find(id, UUID.randomUUID());
            if (current == null) throw new IllegalStateException("Catalog returned an empty reservation");
            ReservationStatus currentStatus = ReservationStatus.tryParse(current.status()).orElse(null);
            if (currentStatus == ReservationStatus.HELD) {
                StockReservation released = inventory.release(id, UUID.randomUUID());
                if (released == null || ReservationStatus.tryParse(released.status()).orElse(null) != ReservationStatus.RELEASED) {
                    throw new IllegalStateException("Catalog did not confirm reservation release");
                }
                recovery.markReleased(id);
            } else if (currentStatus == ReservationStatus.RELEASED || currentStatus == ReservationStatus.EXPIRED) {
                recovery.markReleased(id);
            } else {
                throw new IllegalStateException("Reservation has unexpected state " + current.status());
            }
        } catch (Exception ex) {
            if (ex instanceof com.foodrescue.buildingblocks.http.InternalServiceException serviceError
                    && serviceError.status() == 404 && recovery.markAbsentIfOld(id)) {
                log.info("Reservation {} was never created in Catalog", id);
                return;
            }
            recovery.retryLater(id, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            log.warn("Reservation recovery {} will retry: {}", id, ex.getMessage());
        }
    }
}
