package com.foodrescue.finance;

import static org.junit.jupiter.api.Assertions.*;

import com.foodrescue.finance.domain.exception.FinanceException;
import com.foodrescue.finance.infrastructure.adapter.out.http.VnPayGateway;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class VnPayGatewayTest {
    final VnPayGateway gateway =
            new VnPayGateway(
                    "merchant", "secret", "https://sandbox.test", "http://localhost/result");

    Map<String, String> params() throws Exception {
        Map<String, String> p =
                new TreeMap<>(
                        Map.of(
                                "vnp_TmnCode",
                                "merchant",
                                "vnp_TxnRef",
                                "ref",
                                "vnp_Amount",
                                "4500000",
                                "vnp_ResponseCode",
                                "00",
                                "vnp_TransactionStatus",
                                "00",
                                "vnp_TransactionNo",
                                "123",
                                "vnp_PayDate",
                                "20261127070000"));
        return sign(p);
    }

    Map<String, String> sign(Map<String, String> p) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        p.put(
                "vnp_SecureHash",
                HexFormat.of()
                        .formatHex(
                                mac.doFinal(
                                        VnPayGateway.canonical(p)
                                                .getBytes(StandardCharsets.UTF_8))));
        return p;
    }

    @Test
    void verifiesAndConvertsVnd() throws Exception {
        var c = gateway.verify(params());
        assertEquals(45000, c.amount().longValueExact());
        assertTrue(c.success());
    }

    @Test
    void rejectsTamperedAmount() throws Exception {
        var p = params();
        p.put("vnp_Amount", "1");
        assertEquals(
                "VNPAY_SIGNATURE_INVALID",
                assertThrows(FinanceException.class, () -> gateway.verify(p)).code());
    }

    @Test
    void responseAloneDoesNotMeanSuccess() throws Exception {
        var p = params();
        p.put("vnp_TransactionStatus", "02");
        assertFalse(gateway.verify(sign(p)).success());
    }

    @Test
    void rejectsWrongMerchantEvenWithSignature() throws Exception {
        var p = params();
        p.put("vnp_TmnCode", "other");
        sign(p);
        assertThrows(FinanceException.class, () -> gateway.verify(p));
    }

    @Test
    void encodingMatchesGatewayConvention() {
        assertEquals(
                "vnp_OrderInfo=Thanh+toan+%3A+1",
                VnPayGateway.canonical(
                        Map.of("vnp_OrderInfo", "Thanh toan : 1", "vnp_SecureHash", "ignored")));
    }
}
