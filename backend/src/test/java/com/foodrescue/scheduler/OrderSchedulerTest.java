package com.foodrescue.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderSchedulerTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock RescueBatchRepository rescueBatchRepository;
    @Mock StockService stockService;
    @Mock AuditService auditService;

    OrderScheduler orderScheduler;

    @BeforeEach
    void setUp() {
        orderScheduler = new OrderScheduler(
                orderRepository,
                orderItemRepository,
                rescueBatchRepository,
                stockService,
                auditService);
        ReflectionTestUtils.setField(orderScheduler, "pickupGraceMinutes", 30);
    }

    @Test
    void tuDongHuyDonHetHanThanhToan_chuyenExpiredVaTraKho() {
        Order order = new Order();
        order.setId(10L);
        order.setOrderCode("FR261007-0001");
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setTotalAmount(new BigDecimal("50000"));

        RescueBatch batch = new RescueBatch();
        batch.setId(1L);
        batch.setRemainingQuantity(2);
        batch.setStatus(BatchStatus.AVAILABLE);
        batch.setPickupEnd(LocalDateTime.now().plusHours(2));

        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setOrder(order);
        item.setBatch(batch);
        item.setQuantity(3);

        when(orderRepository.findByStatusAndPaymentExpiresAtBefore(eq(OrderStatus.PENDING_PAYMENT), any()))
                .thenReturn(List.of(order));
        when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of(item));
        when(rescueBatchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(batch));

        orderScheduler.cancelExpiredUnpaidOrders();

        assertEquals(OrderStatus.EXPIRED, order.getStatus());
        assertEquals(PaymentStatus.UNPAID, order.getPaymentStatus());
        assertEquals(5, batch.getRemainingQuantity()); // 2 + 3

        verify(orderRepository).save(order);
        verify(rescueBatchRepository).save(batch);
        verify(stockService).recordMovement(batch, 3, StockReason.RETURNED, order, null, "Hết hạn thanh toán FR261007-0001");
        verify(auditService).log(isNull(), eq(AuditAction.ORDER_EXPIRED), eq(EntityType.ORDER), eq(10L), eq(new BigDecimal("50000")), anyString());
    }

    @Test
    void tuDongChuyenTrangThaiTuiHetHan() {
        RescueBatch batch = new RescueBatch();
        batch.setId(2L);
        batch.setTitle("Combo Burger");
        batch.setStatus(BatchStatus.AVAILABLE);

        when(rescueBatchRepository.findByStatusAndPickupEndBefore(eq(BatchStatus.AVAILABLE), any()))
                .thenReturn(List.of(batch));

        orderScheduler.expireBatches();

        assertEquals(BatchStatus.EXPIRED, batch.getStatus());
        verify(rescueBatchRepository).save(batch);
    }

    @Test
    void tuDongXuLyKhachKhongDenLay_chuyenNoShow() {
        Order order = new Order();
        order.setId(30L);
        order.setOrderCode("FR261007-0003");
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTotalAmount(new BigDecimal("70000"));

        when(orderRepository.findConfirmedOrdersPastPickupGrace(eq(OrderStatus.CONFIRMED), any()))
                .thenReturn(List.of(order));

        orderScheduler.handleNoShowOrders();

        assertEquals(OrderStatus.NO_SHOW, order.getStatus());
        assertNotNull(order.getCompletedAt());

        verify(orderRepository).save(order);
        verify(auditService).log(isNull(), eq(AuditAction.ORDER_NO_SHOW), eq(EntityType.ORDER), eq(30L), eq(new BigDecimal("70000")), anyString());
    }
}
