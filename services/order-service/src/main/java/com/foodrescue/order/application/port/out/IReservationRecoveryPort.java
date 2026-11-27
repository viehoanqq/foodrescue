package com.foodrescue.order.application.port.out;

import java.util.List;
import java.util.UUID;

public interface IReservationRecoveryPort {
    void ensurePending(UUID reservationId);
    void lockPending(UUID reservationId);
    void markOrdered(UUID reservationId, Long orderId);
    void requestRelease(UUID reservationId);
    List<UUID> findDue(int limit);
    boolean tryClaim(UUID reservationId);
    void markReleased(UUID reservationId);
    void markOrderFound(UUID reservationId, Long orderId);
    boolean markAbsentIfOld(UUID reservationId);
    void retryLater(UUID reservationId, String error);
}
