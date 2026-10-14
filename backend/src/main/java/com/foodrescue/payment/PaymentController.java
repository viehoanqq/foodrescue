package com.foodrescue.payment;

import com.foodrescue.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create-url")
    public PaymentResponse createPaymentUrl(
            @Valid @RequestBody CreatePaymentUrlRequest req,
            HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getRemoteAddr();
        }
        return paymentService.createPaymentUrl(CurrentUser.id(), req.orderId(), clientIp);
    }

    @GetMapping("/vnpay/return")
    public PaymentVerifyResponse handleReturn(@RequestParam Map<String, String> allParams) {
        return paymentService.processCallback(allParams);
    }

    @GetMapping("/vnpay/ipn")
    public ResponseEntity<Map<String, String>> handleIpn(@RequestParam Map<String, String> allParams) {
        try {
            PaymentVerifyResponse res = paymentService.processCallback(allParams);
            if (res.success()) {
                return ResponseEntity.ok(Map.of("RspCode", "00", "Message", "Confirm Success"));
            } else {
                return ResponseEntity.ok(Map.of("RspCode", "01", "Message", "Order failed"));
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("RspCode", "99", "Message", "Unknown error: " + e.getMessage()));
        }
    }

    @PostMapping("/mock-success")
    public PaymentVerifyResponse mockSuccess(@Valid @RequestBody CreatePaymentUrlRequest req) {
        return paymentService.mockSuccess(CurrentUser.id(), req.orderId());
    }
}
