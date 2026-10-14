package com.foodrescue.pickup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.foodrescue.common.entity.Order;
import com.foodrescue.common.entity.Store;
import com.foodrescue.common.entity.User;
import com.foodrescue.common.enums.AuditAction;
import com.foodrescue.common.enums.EntityType;
import com.foodrescue.common.enums.OrderStatus;
import com.foodrescue.common.enums.Role;
import com.foodrescue.common.exception.BusinessException;
import com.foodrescue.common.exception.ErrorCode;
import com.foodrescue.common.repository.OrderRepository;
import com.foodrescue.common.repository.UserRepository;
import com.foodrescue.common.service.AuditService;
import com.foodrescue.order.OrderResponse;
import com.foodrescue.order.OrderService;
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
class PickupServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock UserRepository userRepository;
    @Mock OrderService orderService;
    @Mock AuditService auditService;

    PickupService pickupService;

    Order order;
    Store store;
    User staff;
    User customer;

    @BeforeEach
    void setUp() {
        pickupService = new PickupService(orderRepository, userRepository, orderService, auditService);

        store = new Store();
        store.setId(1L);

        staff = new User();
        staff.setId(6L);
        staff.setRole(Role.STORE_STAFF);

        customer = new User();
        customer.setId(8L);
        customer.setFullName("Ma Lý Hoàng Ân");
        customer.setPhone("0987654321");

        LocalDateTime now = LocalDateTime.now();

        order = new Order();
        order.setId(10L);
        order.setOrderCode("FR261007-0001");
        order.setPickupCode("H2R9W7");
        order.setStore(store);
        order.setCustomer(customer);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTotalAmount(new BigDecimal("60000"));
        order.setPickupStart(now.minusHours(1));
        order.setPickupEnd(now.plusHours(1));
    }

    @Test
    void traCuuMaNhanHang_thanhCong() {
        when(orderRepository.findByPickupCode("H2R9W7")).thenReturn(Optional.of(order));
        when(orderService.toOrderResponse(order)).thenReturn(new OrderResponse(
                10L, "FR261007-0001", 1L, "Store 1", "Address", "Phone",
                "Customer", "Phone", 1, new BigDecimal("60000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("60000"), order.getPickupStart(), order.getPickupEnd(), "H2R9W7",
                OrderStatus.CONFIRMED, null, null, null, null, null, null, List.of(), false));

        PickupLookupResponse res = pickupService.lookupByPickupCode(1L, "H2R9W7");

        assertNotNull(res);
        assertEquals("H2R9W7", res.pickupCode());
        assertEquals("FR261007-0001", res.orderCode());
        assertFalse(res.early());
        assertFalse(res.late());
    }

    @Test
    void traCuuMa_saiCuaHang_nemLoiPickupCodeInvalid() {
        when(orderRepository.findByPickupCode("H2R9W7")).thenReturn(Optional.of(order));

        BusinessException ex = assertThrows(BusinessException.class, () -> pickupService.lookupByPickupCode(2L, "H2R9W7"));
        assertEquals(ErrorCode.PICKUP_CODE_INVALID, ex.getCode());
    }

    @Test
    void giaoHangBangMa_thanhCong_chuyenCompletedVaGhiAudit() {
        when(orderRepository.findByPickupCode("H2R9W7")).thenReturn(Optional.of(order));
        when(userRepository.findById(6L)).thenReturn(Optional.of(staff));
        when(orderService.toOrderResponse(order)).thenReturn(new OrderResponse(
                10L, "FR261007-0001", 1L, "Store 1", "Address", "Phone",
                "Customer", "Phone", 1, new BigDecimal("60000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("60000"), order.getPickupStart(), order.getPickupEnd(), "H2R9W7",
                OrderStatus.COMPLETED, null, null, null, null, null, null, List.of(), false));

        OrderResponse res = pickupService.handoverByPickupCode(6L, 1L, "H2R9W7");

        assertNotNull(res);
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertEquals(staff, order.getHandledBy());
        assertNotNull(order.getCompletedAt());

        verify(orderRepository).save(order);
        verify(auditService).log(6L, AuditAction.ORDER_COMPLETED, EntityType.ORDER, 10L, new BigDecimal("60000"), "Giao hàng mã H2R9W7");
    }

    @Test
    void giaoHangBangMa_chuaToiGio_nemLoiPickupTooEarly() {
        order.setPickupStart(LocalDateTime.now().plusHours(1));
        when(orderRepository.findByPickupCode("H2R9W7")).thenReturn(Optional.of(order));

        BusinessException ex = assertThrows(BusinessException.class, () -> pickupService.handoverByPickupCode(6L, 1L, "H2R9W7"));
        assertEquals(ErrorCode.PICKUP_TOO_EARLY, ex.getCode());
    }
}
