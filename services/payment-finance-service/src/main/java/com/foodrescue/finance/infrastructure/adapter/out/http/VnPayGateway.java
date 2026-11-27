package com.foodrescue.finance.infrastructure.adapter.out.http;

import com.foodrescue.finance.application.port.out.PaymentGateway;
import com.foodrescue.finance.domain.exception.FinanceException;
import com.foodrescue.finance.domain.model.FinanceModels.*;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class VnPayGateway implements PaymentGateway {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final String merchant, secret, paymentUrl, returnUrl;

    public VnPayGateway(String merchant, String secret, String paymentUrl, String returnUrl) {
        this.merchant = merchant;
        this.secret = secret;
        this.paymentUrl = paymentUrl;
        this.returnUrl = returnUrl;
    }

    public String url(
            Intent intent, String ref, LocalDateTime now, LocalDateTime expires, String ip) {
        configured();
        Map<String, String> p = new TreeMap<>();
        p.put("vnp_Version", "2.1.0");
        p.put("vnp_Command", "pay");
        p.put("vnp_TmnCode", merchant);
        p.put(
                "vnp_Amount",
                intent.totalAmount()
                        .multiply(BigDecimal.valueOf(100))
                        .toBigIntegerExact()
                        .toString());
        p.put("vnp_CurrCode", "VND");
        p.put("vnp_TxnRef", ref);
        p.put("vnp_OrderInfo", "Thanh toan don " + intent.orderId());
        p.put("vnp_OrderType", "other");
        p.put("vnp_Locale", "vn");
        p.put("vnp_ReturnUrl", returnUrl);
        p.put("vnp_IpAddr", ip);
        p.put("vnp_CreateDate", DATE.format(now));
        p.put("vnp_ExpireDate", DATE.format(expires));
        String query = canonical(p);
        return paymentUrl + "?" + query + "&vnp_SecureHash=" + sign(query);
    }

    public Callback verify(Map<String, String> params) {
        configured();
        String supplied = params.getOrDefault("vnp_SecureHash", "");
        if (!supplied.matches("[a-fA-F0-9]{128}")
                || !MessageDigest.isEqual(
                        supplied.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                        sign(canonical(params)).getBytes(StandardCharsets.US_ASCII)))
            throw new FinanceException(400, "VNPAY_SIGNATURE_INVALID", "Chữ ký VNPay không hợp lệ");
        try {
            if (!merchant.equals(params.get("vnp_TmnCode"))) throw new IllegalArgumentException();
            String ref = required(params, "vnp_TxnRef", 64),
                    response = required(params, "vnp_ResponseCode", 10);
            String status = required(params, "vnp_TransactionStatus", 10);
            String raw = required(params, "vnp_Amount", 16);
            if (!raw.matches("[0-9]+")) throw new IllegalArgumentException();
            BigDecimal amount =
                    new BigDecimal(raw)
                            .divide(BigDecimal.valueOf(100))
                            .setScale(0, java.math.RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0) throw new IllegalArgumentException();
            boolean success = "00".equals(response) && "00".equals(status);
            String number =
                    success
                            ? required(params, "vnp_TransactionNo", 64)
                            : params.get("vnp_TransactionNo");
            String bank = params.get("vnp_BankCode");
            if (bank != null && bank.length() > 20) throw new IllegalArgumentException();
            LocalDateTime paid =
                    success ? LocalDateTime.parse(required(params, "vnp_PayDate", 14), DATE) : null;
            return new Callback(ref, amount, response, status, number, bank, paid);
        } catch (RuntimeException ex) {
            throw new FinanceException(400, "VALIDATION_ERROR", "Tham số VNPay không hợp lệ");
        }
    }

    private String required(Map<String, String> p, String key, int max) {
        String v = p.get(key);
        if (v == null || v.isBlank() || v.length() > max) throw new IllegalArgumentException();
        return v;
    }

    public static String canonical(Map<String, String> params) {
        return new TreeMap<>(params)
                .entrySet().stream()
                        .filter(
                                e ->
                                        e.getKey().startsWith("vnp_")
                                                && !Set.of("vnp_SecureHash", "vnp_SecureHashType")
                                                        .contains(e.getKey()))
                        .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                        .collect(java.util.stream.Collectors.joining("&"));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.US_ASCII);
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private void configured() {
        if (merchant.isBlank() || secret.isBlank())
            throw new FinanceException(503, "DEPENDENCY_UNAVAILABLE", "Chưa cấu hình VNPay");
    }
}
