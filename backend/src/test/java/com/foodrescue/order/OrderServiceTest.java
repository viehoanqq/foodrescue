package com.foodrescue.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.foodrescue.common.entity.*;
import com.foodrescue.common.enums.*;
import com.foodrescue.common.exception.BusinessException;
import com.foodrescue.common.exception.ErrorCode;
import com.foodrescue.common.repository.*;
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

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock RescueBatchRepository rescueBatchRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock RefundRepository refundRepository;
    @Mock StockService stockService;
    @Mock AuditService auditService;

    OrderService orderService;

    User customer;
    User storeOwner;
    Store store;
    RescueBatch batch1;
    RescueBatch batch2;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                rescueBatchRepository,
                cartItemRepository,
                userRepository,
                paymentRepository,
                refundRepository,
                stockService,
                auditService);

        customer = new User();
        customer.setId(8L);
        customer.setFullName("Ma Lý Hoàng Ân");
        customer.setPhone("0987654321");
        customer.setRole(Role.CUSTOMER);

        storeOwner = new User();
        storeOwner.setId(2L);
        storeOwner.setFullName("Nguyễn Văn Phát");
        storeOwner.setRole(Role.STORE_OWNER);

        store = new Store();
        store.setId(1L);
        store.setName("Bánh Mì An Phát");
        store.setCommissionRate(new BigDecimal("15.00"));
        store.setOwner(storeOwner);

        LocalDateTime now = LocalDateTime.now();

        batch1 = new RescueBatch();
        batch1.setId(1L);
        batch1.setTitle("Túi Bánh Mì Bất Ngờ");
        batch1.setOriginalPrice(new BigDecimal("80000"));
        batch1.setRescuePrice(new BigDecimal("30000"));
        batch1.setQuantity(10);
        batch1.setRemainingQuantity(8);
        batch1.setStatus(BatchStatus.AVAILABLE);
        batch1.setPickupStart(now.plusHours(1));
        batch1.setPickupEnd(now.plusHours(3));
        batch1.setStore(store);

        batch2 = new RescueBatch();
        batch2.setId(2L);
        batch2.setTitle("Combo Hamburger");
        batch2.setOriginalPrice(new BigDecimal("95000"));
        batch2.setRescuePrice(new BigDecimal("40000"));
        batch2.setQuantity(5);
        batch2.setRemainingQuantity(5);
        batch2.setStatus(BatchStatus.AVAILABLE);
        batch2.setPickupStart(now.plusHours(1));
        batch2.setPickupEnd(now.plusHours(2)); // Giao nhau: plusHours(1) -> plusHours(2)
        batch2.setStore(store);
    }

    @Test
    void taoDonNhieuTui_thanhCong_truTonKhoVaGhiSoKho() {
        when(userRepository.findById(8L)).thenReturn(Optional.of(customer));
        when(rescueBatchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(batch1));
        when(rescueBatchRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(batch2));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> {
            OrderItem oi = inv.getArgument(0);
            oi.setId(101L);
            return oi;
        });

        CreateOrderRequest req = new CreateOrderRequest(List.of(
                new CreateOrderItemRequest(1L, 2),
                new CreateOrderItemRequest(2L, 1)));

        OrderResponse res = orderService.createOrder(8L, req);

        assertNotNull(res);
        assertEquals(3, res.itemCount()); // 2 + 1
        assertEquals(new BigDecimal("100000"), res.totalAmount()); // 30.000 * 2 + 40.000 * 1
        assertEquals(new BigDecimal("15000"), res.commissionAmount()); // 15% của 100.000
        assertEquals(new BigDecimal("85000"), res.storeEarning());
        assertEquals(OrderStatus.PENDING_PAYMENT, res.status());
        assertEquals(PaymentStatus.UNPAID, res.paymentStatus());
        assertNotNull(res.pickupCode());
        assertEquals(6, res.pickupCode().length());

        // Kiểm tra chống bán vượt: remaining_quantity bị trừ
        assertEquals(6, batch1.getRemainingQuantity()); // 8 - 2
        assertEquals(4, batch2.getRemainingQuantity()); // 5 - 1

        // Kiểm tra ghi sổ kho ORDERED
        verify(stockService, times(2)).recordMovement(any(), anyInt(), eq(StockReason.ORDERED), any(), eq(customer), anyString());
        // Kiểm tra xoá khỏi giỏ
        verify(cartItemRepository, times(2)).deleteById(any(CartItemId.class));
    }

    @Test
    void taoDon_soLuongVuotTonKho_nemLoiStockNotEnough() {
        when(userRepository.findById(8L)).thenReturn(Optional.of(customer));
        batch1.setRemainingQuantity(1);
        when(rescueBatchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(batch1));

        CreateOrderRequest req = new CreateOrderRequest(List.of(
                new CreateOrderItemRequest(1L, 3)));

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.createOrder(8L, req));
        assertEquals(ErrorCode.STOCK_NOT_ENOUGH, ex.getCode());
    }

    @Test
    void taoDon_tuiHetHang_nemLoiBatchSoldOut() {
        when(userRepository.findById(8L)).thenReturn(Optional.of(customer));
        batch1.setRemainingQuantity(0);
        when(rescueBatchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(batch1));

        CreateOrderRequest req = new CreateOrderRequest(List.of(
                new CreateOrderItemRequest(1L, 1)));

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.createOrder(8L, req));
        assertEquals(ErrorCode.BATCH_SOLD_OUT, ex.getCode());
    }

    @Test
    void taoDon_khongCoKhungGioGiaoNhau_nemLoiPickupConflict() {
        when(userRepository.findById(8L)).thenReturn(Optional.of(customer));
        LocalDateTime now = LocalDateTime.now();
        batch1.setPickupStart(now.plusHours(1));
        batch1.setPickupEnd(now.plusHours(2));

        batch2.setPickupStart(now.plusHours(3));
        batch2.setPickupEnd(now.plusHours(4));

        when(rescueBatchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(batch1));
        when(rescueBatchRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(batch2));

        CreateOrderRequest req = new CreateOrderRequest(List.of(
                new CreateOrderItemRequest(1L, 1),
                new CreateOrderItemRequest(2L, 1)));

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.createOrder(8L, req));
        assertEquals(ErrorCode.PICKUP_WINDOW_CONFLICT, ex.getCode());
    }

    @Test
    void khachHuyDonChuaThanhToan_thanhCong_hoanTraKhoVaGhiSoKho() {
        Order order = new Order();
        order.setId(10L);
        order.setOrderCode("FR261007-0001");
        order.setCustomer(customer);
        order.setStore(store);
        order.setTotalAmount(new BigDecimal("60000"));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentStatus(PaymentStatus.UNPAID);

        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setOrder(order);
        item.setBatch(batch1);
        item.setQuantity(2);

        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of(item));
        when(rescueBatchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(batch1));

        OrderResponse res = orderService.cancelCustomerOrder(8L, 10L, "Đổi ý không mua nữa");

        assertEquals(OrderStatus.CANCELLED, res.status());
        assertEquals("Đổi ý không mua nữa", order.getCancelReason());
        assertEquals(customer, order.getCancelledBy());

        // Kiểm tra trả lại kho
        assertEquals(10, batch1.getRemainingQuantity()); // 8 + 2
        verify(stockService).recordMovement(batch1, 2, StockReason.RETURNED, order, customer, "Huỷ đơn FR261007-0001");
        verify(auditService).log(8L, AuditAction.ORDER_CANCELLED, EntityType.ORDER, 10L, new BigDecimal("60000"), "Đổi ý không mua nữa");
    }

    @Test
    void cuaHangHuyDonDaThanhToan_taoRefundVaTraKho() {
        Order order = new Order();
        order.setId(20L);
        order.setOrderCode("FR261007-0002");
        order.setCustomer(customer);
        order.setStore(store);
        order.setTotalAmount(new BigDecimal("80000"));
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);

        OrderItem item = new OrderItem();
        item.setId(2L);
        item.setOrder(order);
        item.setBatch(batch1);
        item.setQuantity(1);

        Payment payment = new Payment();
        payment.setId(99L);
        payment.setOrder(order);
        payment.setAmount(new BigDecimal("80000"));
        payment.setStatus(PaymentTxnStatus.SUCCESS);

        when(orderRepository.findById(20L)).thenReturn(Optional.of(order));
        when(userRepository.findById(2L)).thenReturn(Optional.of(storeOwner));
        when(orderItemRepository.findByOrderId(20L)).thenReturn(List.of(item));
        when(rescueBatchRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(batch1));
        when(paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(20L, PaymentTxnStatus.SUCCESS))
                .thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> {
            Refund r = inv.getArgument(0);
            r.setId(50L);
            return r;
        });

        OrderResponse res = orderService.cancelStoreOrder(2L, 1L, 20L, "Hết bánh đột xuất");

        assertEquals(OrderStatus.CANCELLED, res.status());
        assertEquals(PaymentStatus.REFUND_PENDING, order.getPaymentStatus());
        assertEquals("Hết bánh đột xuất", order.getCancelReason());

        // Kiểm tra trả lại kho
        assertEquals(9, batch1.getRemainingQuantity()); // 8 + 1
        verify(stockService).recordMovement(batch1, 1, StockReason.RETURNED, order, storeOwner, "Cửa hàng huỷ đơn FR261007-0002");

        // Kiểm tra tạo bản ghi Refund
        verify(refundRepository).save(any(Refund.class));
        verify(auditService).log(eq(2L), eq(AuditAction.REFUND_REQUESTED), eq(EntityType.REFUND), eq(50L), eq(new BigDecimal("80000")), anyString());
        verify(auditService).log(eq(2L), eq(AuditAction.ORDER_CANCELLED), eq(EntityType.ORDER), eq(20L), eq(new BigDecimal("80000")), eq("Hết bánh đột xuất"));
    }
}
