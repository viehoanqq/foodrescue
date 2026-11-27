package com.foodrescue.finance.api;

import com.foodrescue.buildingblocks.http.InternalKeyVerifier;
import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.finance.application.port.out.*;
import com.foodrescue.finance.application.usecase.FinanceUseCase;
import com.foodrescue.finance.domain.model.FinanceModels.*;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class FinanceController {
    private final FinanceUseCase useCase;
    private final FinanceLedger ledger;
    private final FinanceDependencies dependencies;
    private final FinanceReports reports;
    private final InternalKeyVerifier keys;

    public FinanceController(
            FinanceUseCase useCase,
            FinanceLedger ledger,
            FinanceDependencies dependencies,
            FinanceReports reports,
            @Value("${app.internal-api-key}") String key) {
        this.useCase = useCase;
        this.ledger = ledger;
        this.dependencies = dependencies;
        this.reports = reports;
        keys = new InternalKeyVerifier(key);
    }

    public record PaymentRequest(@Positive long orderId) {}

    public record Reference(@NotBlank @Size(max = 100) String gatewayRef) {}

    public record Transfer(@NotBlank @Size(max = 100) String transferRef) {}

    @PostMapping("/api/payments/vnpay-url")
    public Link payment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody @Valid PaymentRequest body,
            HttpServletRequest request) {
        return useCase.payment(
                user.id(), body.orderId(), key, request.getRemoteAddr(), correlation(request));
    }

    @GetMapping("/api/payments/vnpay/ipn")
    public Map<String, String> ipn(
            @RequestParam Map<String, String> params, HttpServletRequest request) {
        return useCase.ipn(params, UUID.randomUUID());
    }

    @GetMapping("/api/payments/vnpay/return")
    public Map<String, Object> result(@RequestParam Map<String, String> params) {
        return useCase.result(params);
    }

    @GetMapping("/internal/payments/orders/{order}/status")
    public Map<String, Object> status(
            @PathVariable long order, @RequestHeader("X-Internal-Key") String key) {
        keys.verify(key);
        return ledger.paymentStatus(order);
    }

    @GetMapping("/api/admin/refunds")
    public Map<String, Object> refunds(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reports.list("refunds", null, status, page, size);
    }

    @GetMapping("/api/admin/refunds/{id}")
    public Map<String, Object> refund(@PathVariable long id) {
        return reports.detail("refunds", id, null);
    }

    @PostMapping("/api/admin/refunds/{id}/complete")
    public Map<String, Object> complete(
            @PathVariable long id,
            @RequestBody @Valid Reference body,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        return ledger.completeRefund(id, body.gatewayRef(), user.id(), correlation(request));
    }

    @GetMapping("/api/admin/payouts")
    public Map<String, Object> payouts(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reports.list("payouts", storeId, status, page, size);
    }

    @GetMapping("/api/admin/payouts/{id}")
    public Map<String, Object> payout(@PathVariable long id) {
        return reports.detail("payouts", id, null);
    }

    @PostMapping("/api/admin/payouts")
    public Map<String, Object> createPayout(
            @RequestBody PayoutRequest body,
            @RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        return useCase.payout(body, user.id(), key, correlation(request));
    }

    @PostMapping("/api/admin/payouts/{id}/paid")
    public Map<String, Object> paid(
            @PathVariable long id,
            @RequestBody @Valid Transfer body,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        return ledger.changePayout(id, "PAID", body.transferRef(), user.id(), correlation(request));
    }

    @PostMapping("/api/admin/payouts/{id}/cancel")
    public Map<String, Object> cancel(
            @PathVariable long id,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request) {
        return ledger.changePayout(id, "CANCELLED", null, user.id(), correlation(request));
    }

    @GetMapping("/api/store/payouts")
    public Map<String, Object> storePayouts(
            @RequestHeader("Authorization") String auth,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reports.list("payouts", dependencies.ownerStore(auth), status, page, size);
    }

    @GetMapping("/api/store/payouts/{id}")
    public Map<String, Object> storePayout(
            @RequestHeader("Authorization") String auth, @PathVariable long id) {
        return reports.detail("payouts", id, dependencies.ownerStore(auth));
    }

    @GetMapping("/api/store/revenue")
    public Map<String, Object> revenue(
            @RequestHeader("Authorization") String auth,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return reports.revenue(dependencies.ownerStore(auth), from, to);
    }

    @GetMapping("/api/store/statement")
    public Map<String, Object> statement(
            @RequestHeader("Authorization") String auth,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reports.statement(dependencies.ownerStore(auth), from, to, page, size);
    }

    @GetMapping({"/api/admin/finance", "/api/admin/finance/reconciliation"})
    public Map<String, Object> reconciliation() {
        return reports.reconciliation();
    }

    @GetMapping("/api/admin/finance/payments")
    public Map<String, Object> payments(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reports.list("payments", null, status, page, size);
    }

    @GetMapping("/api/admin/dashboard")
    public Map<String, Object> dashboard(
            @RequestParam LocalDate from, @RequestParam LocalDate to, HttpServletRequest request) {
        var result = new LinkedHashMap<>(reports.revenue(null, from, to));
        result.put("counts", dependencies.counts(correlation(request)));
        return result;
    }

    private UUID correlation(HttpServletRequest request) {
        String value = request.getHeader("X-Correlation-Id");
        return value == null ? UUID.randomUUID() : UUID.fromString(value);
    }
}
