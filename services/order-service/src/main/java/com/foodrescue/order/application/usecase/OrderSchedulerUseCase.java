package com.foodrescue.order.application.usecase;

import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderSchedulerUseCase {
    private static final Logger log = LoggerFactory.getLogger(OrderSchedulerUseCase.class);

    private final SpringDataOrderRepository orderRepository;
    private final InventoryReleaseService inventoryReleaseService;
    private final OrderSupport orderSupport;
    private final TransactionTemplate transactionTemplate;

    public OrderSchedulerUseCase(
            SpringDataOrderRepository orderRepository,
            InventoryReleaseService inventoryReleaseService,
            OrderSupport orderSupport,
            PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.inventoryReleaseService = inventoryReleaseService;
        this.orderSupport = orderSupport;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 60000) // Mỗi 1 phút
    public void expireUnpaidOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<OrderEntity> expiredOrders = orderRepository.findExpiredPendingOrders("PENDING_PAYMENT", now);
        for (OrderEntity candidate : expiredOrders) {
            transactionTemplate.executeWithoutResult(tx -> {
                OrderEntity order = orderRepository.findByIdForUpdate(candidate.getId()).orElse(null);
                if (order == null || !"PENDING_PAYMENT".equalsIgnoreCase(order.getStatus())) {
                    return;
                }
                order.setStatus("EXPIRED");
                order.setCancelReason("Hết hạn thanh toán (quá 15 phút)");
                order.setAggregateVersion(order.getAggregateVersion() + 1);
                orderRepository.save(order);

                inventoryReleaseService.releaseOrEnqueue(order.getReservationId(), "PAYMENT_EXPIRED", UUID.randomUUID());
                orderSupport.emitOrderStateChanged(order, null, UUID.randomUUID(), null);
                log.info("Expired unpaid order: {}", order.getOrderCode());
            });
        }
    }

    @Scheduled(fixedDelay = 300000) // Mỗi 5 phút
    public void markNoShowOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(30);
        List<OrderEntity> noShowOrders = orderRepository.findNoShowOrders("CONFIRMED", deadline);
        for (OrderEntity candidate : noShowOrders) {
            transactionTemplate.executeWithoutResult(tx -> {
                OrderEntity order = orderRepository.findByIdForUpdate(candidate.getId()).orElse(null);
                if (order == null || !"CONFIRMED".equalsIgnoreCase(order.getStatus())) {
                    return;
                }
                order.setStatus("NO_SHOW");
                order.setCompletedAt(LocalDateTime.now());
                order.setCancelReason("Khách hàng không đến nhận trong khung giờ");
                order.setAggregateVersion(order.getAggregateVersion() + 1);
                orderRepository.save(order);

                orderSupport.emitOrderStateChanged(order, null, UUID.randomUUID(), null);
                log.info("Marked order as NO_SHOW: {}", order.getOrderCode());
            });
        }
    }

    @Scheduled(fixedDelay = 30000) // Mỗi 30 giây thử trả kho lại cho các đơn bị lỗi release trước đó
    public void retryFailedInventoryReleases() {
        inventoryReleaseService.retryPendingReleases();
    }
}
