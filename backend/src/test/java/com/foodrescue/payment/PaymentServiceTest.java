package com.foodrescue.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.foodrescue.common.entity.Order;
import com.foodrescue.common.entity.Payment;
import com.foodrescue.common.entity.User;
import com.foodrescue.common.enums.AuditAction;
import com.foodrescue.common.enums.EntityType;
import com.foodrescue.common.enums.OrderStatus;
import com.foodrescue.common.enums.PaymentStatus;
import com.foodrescue.common.enums.PaymentTxnStatus;
import com.foodrescue.common.exception.BusinessException;
import com.foodrescue.common.exception.ErrorCode;
import com.foodrescue.common.repository.OrderRepository;
import com.foodrescue.common.repository.PaymentRepository;
import com.foodrescue.common.service.AuditService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock AuditService auditService;

    PaymentService paymentService;

    Order order;
    User customer;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(orderRepository, paymentRepository, auditService);
        ReflectionTestUtils.setField(paymentService, "tmnCode", "DEMOV210");
        ReflectionTestUtils.setField(paymentService, "hashSecret", "RAOCTVSQ2TGKDAGNAYGHISDXG6O9YKFF");
        ReflectionTestUtils.setField(paymentService, "payUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(paymentService, "returnUrl", "http://localhost:5173/payment/result");

        customer = new User();
        customer.setId(8L);

        order = new Order();
        order.setId(10L);
        order.setOrderCode("FR261007-0001");
        order.setCustomer(customer);
        order.setTotalAmount(new BigDecimal("60000"));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setPaymentExpiresAt(LocalDateTime.now().plusMinutes(10));
    }

    @Test
    void taoPaymentUrl_thanhCong() {
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(10L)).thenReturn(List.of());

        PaymentResponse res = paymentService.createPaymentUrl(8L, 10L, "127.0.0.1");

        assertNotNull(res);
        assertNotNull(res.paymentUrl());
        assertTrue(res.paymentUrl().contains("vnp_SecureHash="));
        assertTrue(res.paymentUrl().contains("vnp_Amount=6000000"));
        assertEquals(new BigDecimal("60000"), res.amount());

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void xuLyCallback_thanhCong_chuyenTrangThaiVaGhiAudit() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setOrder(order);
        payment.setAmount(new BigDecimal("60000"));
        payment.setTxnRef("FR261007-0001-1");
        payment.setStatus(PaymentTxnStatus.PENDING);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "FR261007-0001-1");
        params.put("vnp_Amount", "6000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "14567890");
        params.put("vnp_BankCode", "NCB");

        String hash = VNPayUtil.hashAllFields(params, "RAOCTVSQ2TGKDAGNAYGHISDXG6O9YKFF");
        params.put("vnp_SecureHash", hash);

        when(paymentRepository.findByTxnRef("FR261007-0001-1")).thenReturn(Optional.of(payment));

        PaymentVerifyResponse res = paymentService.processCallback(params);

        assertTrue(res.success());
        assertEquals(PaymentTxnStatus.SUCCESS, payment.getStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());

        verify(orderRepository).save(order);
        verify(paymentRepository).save(payment);
        verify(auditService).log(isNull(), eq(AuditAction.PAYMENT_SUCCESS), eq(EntityType.ORDER), eq(10L), eq(new BigDecimal("60000")), anyString());
    }

    @Test
    void xuLyCallback_saiChuKy_nemLoiInvalidSignature() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "FR261007-0001-1");
        params.put("vnp_SecureHash", "SAI_CHU_KY_123456");

        BusinessException ex = assertThrows(BusinessException.class, () -> paymentService.processCallback(params));
        assertEquals(ErrorCode.PAYMENT_INVALID_SIGNATURE, ex.getCode());
    }
}
