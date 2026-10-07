package com.foodrescue.scheduler;

import com.foodrescue.common.entity.Order;
import com.foodrescue.common.entity.OrderItem;
import com.foodrescue.common.entity.RescueBatch;
import com.foodrescue.common.enums.AuditAction;
import com.foodrescue.common.enums.BatchStatus;
import com.foodrescue.common.enums.EntityType;
import com.foodrescue.common.enums.OrderStatus;
import com.foodrescue.common.enums.PaymentStatus;
import com.foodrescue.common.enums.StockReason;
import com.foodrescue.common.repository.OrderItemRepository;
import com.foodrescue.common.repository.OrderRepository;
import com.foodrescue.common.repository.RescueBatchRepository;
import com.foodrescue.common.service.AuditService;
import com.foodrescue.common.service.StockService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TV4 (O6) - Tác vụ tự động: huỷ đơn chưa thanh toán, hết hạn túi, không đến lấy.
 */
@Component
public class OrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderScheduler.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final RescueBatchRepository rescueBatchRepository;
    private final StockService stockService;
    private final AuditService auditService;

    @Value("${app.order.pickup-grace-minutes:30}")
    private int pickupGraceMinutes;

    public OrderScheduler(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            RescueBatchRepository rescueBatchRepository,
            StockService stockService,
            AuditService auditService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.rescueBatchRepository = rescueBatchRepository;
        this.stockService = stockService;
        this.auditService = auditService;
    }

    /**
     * Mỗi phút kiểm tra các đơn chưa thanh toán quá hạn (10-15 phút) -> chuyển EXPIRED, trả lại kho.
     */
    @Scheduled(cron = "0 */1 * * * *")
    @Transactional
    public void cancelExpiredUnpaidOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> expiredOrders = orderRepository.findByStatusAndPaymentExpiresAtBefore(
                OrderStatus.PENDING_PAYMENT, now);

        for (Order order : expiredOrders) {
            log.info("Tu dong huy don het han thanh toan: {}", order.getOrderCode());

            order.setStatus(OrderStatus.EXPIRED);
            order.setPaymentStatus(PaymentStatus.UNPAID);
            orderRepository.save(order);

            // Hoàn lại kho
            List<OrderItem> items = new ArrayList<>(orderItemRepository.findByOrderId(order.getId()));
            items.sort(Comparator.comparing(i -> i.getBatch().getId()));

            for (OrderItem item : items) {
                RescueBatch batch = rescueBatchRepository.findByIdForUpdate(item.getBatch().getId()).orElse(null);
                if (batch != null) {
                    batch.setRemainingQuantity(batch.getRemainingQuantity() + item.getQuantity());
                    if (batch.getStatus() == BatchStatus.SOLD_OUT && batch.getPickupEnd().isAfter(now)) {
                        batch.setStatus(BatchStatus.AVAILABLE);
                    }
                    rescueBatchRepository.save(batch);

                    stockService.recordMovement(
                            batch,
                            item.getQuantity(),
                            StockReason.RETURNED,
                            order,
                            null,
                            "Hết hạn thanh toán " + order.getOrderCode());
                }
            }

            auditService.log(
                    null,
                    AuditAction.ORDER_EXPIRED,
                    EntityType.ORDER,
                    order.getId(),
                    order.getTotalAmount(),
                    "Hết hạn thanh toán");
        }
    }

    /**
     * Mỗi phút kiểm tra các túi hết giờ nhận hàng -> chuyển EXPIRED.
     */
    @Scheduled(cron = "0 */1 * * * *")
    @Transactional
    public void expireBatches() {
        LocalDateTime now = LocalDateTime.now();
        List<RescueBatch> batches = rescueBatchRepository.findByStatusAndPickupEndBefore(
                BatchStatus.AVAILABLE, now);

        for (RescueBatch batch : batches) {
            log.info("Tui het gio nhan hang: id={}, title={}", batch.getId(), batch.getTitle());
            batch.setStatus(BatchStatus.EXPIRED);
            rescueBatchRepository.save(batch);
        }
    }

    /**
     * Mỗi phút kiểm tra các đơn đã xác nhận nhưng quá giờ nhận + grace period (30 phút) -> chuyển NO_SHOW.
     */
    @Scheduled(cron = "0 */1 * * * *")
    @Transactional
    public void handleNoShowOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(pickupGraceMinutes);
        List<Order> noShowOrders = orderRepository.findConfirmedOrdersPastPickupGrace(
                OrderStatus.CONFIRMED, cutoff);

        for (Order order : noShowOrders) {
            log.info("Don hang khach khong den lay: {}", order.getOrderCode());
            LocalDateTime now = LocalDateTime.now();
            order.setStatus(OrderStatus.NO_SHOW);
            order.setCompletedAt(now);
            orderRepository.save(order);

            auditService.log(
                    null,
                    AuditAction.ORDER_NO_SHOW,
                    EntityType.ORDER,
                    order.getId(),
                    order.getTotalAmount(),
                    "Quá giờ nhận " + pickupGraceMinutes + " phút");
        }
    }
}
