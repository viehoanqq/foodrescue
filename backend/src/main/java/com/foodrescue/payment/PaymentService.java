package com.foodrescue.payment;

import com.foodrescue.common.entity.Order;
import com.foodrescue.common.entity.Payment;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AuditService auditService;

    @Value("${app.vnpay.tmn-code:}")
    private String tmnCode;

    @Value("${app.vnpay.hash-secret:}")
    private String hashSecret;

    @Value("${app.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;

    @Value("${app.vnpay.return-url:http://localhost:5173/payment/result}")
    private String returnUrl;

    private static final String DEFAULT_SANDBOX_TMN = "DEMOV210";
    private static final String DEFAULT_SANDBOX_SECRET = "RAOCTVSQ2TGKDAGNAYGHISDXG6O9YKFF";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public PaymentService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            AuditService auditService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.auditService = auditService;
    }

    private String getEffectiveTmnCode() {
        return (tmnCode != null && !tmnCode.isBlank()) ? tmnCode : DEFAULT_SANDBOX_TMN;
    }

    private String getEffectiveHashSecret() {
        return (hashSecret != null && !hashSecret.isBlank()) ? hashSecret : DEFAULT_SANDBOX_SECRET;
    }

    @Transactional
    public PaymentResponse createPaymentUrl(Long customerId, Long orderId, String clientIp) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!order.getCustomer().getId().equals(customerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || order.getPaymentStatus() != PaymentStatus.UNPAID) {
            throw new BusinessException(ErrorCode.DATA_CONSTRAINT_VIOLATION);
        }

        LocalDateTime now = LocalDateTime.now();
        if (order.getPaymentExpiresAt().isBefore(now)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }

        List<Payment> existingPayments = paymentRepository.findByOrderId(order.getId());
        String txnRef = order.getOrderCode() + "-" + (existingPayments.size() + 1);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setTxnRef(txnRef);
        payment.setStatus(PaymentTxnStatus.PENDING);
        paymentRepository.save(payment);

        String vnpTmnCode = getEffectiveTmnCode();
        String vnpSecret = getEffectiveHashSecret();

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", vnpTmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(order.getTotalAmount().longValue() * 100));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", txnRef);
        vnpParams.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrderCode());
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", returnUrl);
        vnpParams.put("vnp_IpAddr", (clientIp != null && !clientIp.isBlank()) ? clientIp : "127.0.0.1");
        vnpParams.put("vnp_CreateDate", DATE_FMT.format(now));
        vnpParams.put("vnp_ExpireDate", DATE_FMT.format(order.getPaymentExpiresAt()));

        String queryUrl = VNPayUtil.buildQueryUrl(vnpParams);
        String secureHash = VNPayUtil.hashAllFields(vnpParams, vnpSecret);
        String paymentUrl = payUrl + "?" + queryUrl + "&vnp_SecureHash=" + secureHash;

        return new PaymentResponse(paymentUrl, txnRef, order.getTotalAmount(), order.getOrderCode());
    }

    @Transactional
    public PaymentVerifyResponse processCallback(Map<String, String> allParams) {
        String vnpSecureHash = allParams.get("vnp_SecureHash");
        if (vnpSecureHash == null || vnpSecureHash.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_SIGNATURE);
        }

        Map<String, String> fields = new HashMap<>();
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String k = entry.getKey();
            String v = entry.getValue();
            if (v != null && !v.isEmpty() && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")) {
                fields.put(k, v);
            }
        }

        String calculatedHash = VNPayUtil.hashAllFields(fields, getEffectiveHashSecret());
        if (!calculatedHash.equalsIgnoreCase(vnpSecureHash)) {
            throw new BusinessException(ErrorCode.PAYMENT_INVALID_SIGNATURE);
        }

        String txnRef = allParams.get("vnp_TxnRef");
        Payment payment = paymentRepository.findByTxnRef(txnRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        Order order = payment.getOrder();
        String responseCode = allParams.get("vnp_ResponseCode");
        String transactionNo = allParams.get("vnp_TransactionNo");
        String bankCode = allParams.get("vnp_BankCode");
        String vnpAmountStr = allParams.get("vnp_Amount");

        if (vnpAmountStr != null) {
            long actualAmount = Long.parseLong(vnpAmountStr);
            long expectedAmount = payment.getAmount().longValue() * 100;
            if (actualAmount != expectedAmount) {
                throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
            }
        }

        if (payment.getStatus() == PaymentTxnStatus.PENDING) {
            if ("00".equals(responseCode)) {
                LocalDateTime now = LocalDateTime.now();
                payment.setStatus(PaymentTxnStatus.SUCCESS);
                payment.setGatewayTxnNo(transactionNo != null ? transactionNo : "MOCK-" + System.currentTimeMillis());
                payment.setBankCode(bankCode != null ? bankCode : "NCB");
                payment.setResponseCode(responseCode);
                payment.setPaidAt(now);
                paymentRepository.save(payment);

                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    order.setStatus(OrderStatus.CONFIRMED);
                    order.setPaymentStatus(PaymentStatus.PAID);
                    orderRepository.save(order);

                    auditService.log(
                            null,
                            AuditAction.PAYMENT_SUCCESS,
                            EntityType.ORDER,
                            order.getId(),
                            order.getTotalAmount(),
                            "VNPay " + payment.getGatewayTxnNo());
                }

                return new PaymentVerifyResponse(true, order.getId(), order.getOrderCode(), payment.getAmount(), "Thanh toán thành công");
            } else {
                payment.setStatus(PaymentTxnStatus.FAILED);
                payment.setResponseCode(responseCode);
                paymentRepository.save(payment);

                return new PaymentVerifyResponse(false, order.getId(), order.getOrderCode(), payment.getAmount(), "Thanh toán thất bại");
            }
        }

        boolean isSuccess = payment.getStatus() == PaymentTxnStatus.SUCCESS;
        return new PaymentVerifyResponse(
                isSuccess,
                order.getId(),
                order.getOrderCode(),
                payment.getAmount(),
                isSuccess ? "Thanh toán thành công" : "Thanh toán không thành công");
    }

    @Transactional
    public PaymentVerifyResponse mockSuccess(Long customerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!order.getCustomer().getId().equals(customerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.DATA_CONSTRAINT_VIOLATION);
        }

        LocalDateTime now = LocalDateTime.now();
        List<Payment> payments = paymentRepository.findByOrderId(order.getId());
        Payment payment;
        if (!payments.isEmpty() && payments.get(payments.size() - 1).getStatus() == PaymentTxnStatus.PENDING) {
            payment = payments.get(payments.size() - 1);
        } else {
            payment = new Payment();
            payment.setOrder(order);
            payment.setAmount(order.getTotalAmount());
            payment.setTxnRef(order.getOrderCode() + "-" + (payments.size() + 1));
        }

        String txnNo = "SIM" + (System.currentTimeMillis() % 100000000);
        payment.setStatus(PaymentTxnStatus.SUCCESS);
        payment.setGatewayTxnNo(txnNo);
        payment.setBankCode("NCB");
        payment.setResponseCode("00");
        payment.setPaidAt(now);
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        auditService.log(
                null,
                AuditAction.PAYMENT_SUCCESS,
                EntityType.ORDER,
                order.getId(),
                order.getTotalAmount(),
                "VNPay " + txnNo);

        return new PaymentVerifyResponse(true, order.getId(), order.getOrderCode(), payment.getAmount(), "Thanh toán thành công qua sandbox");
    }
}
