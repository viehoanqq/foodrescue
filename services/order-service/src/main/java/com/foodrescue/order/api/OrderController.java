package com.foodrescue.order.api;

import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.application.usecase.CreateOrderUseCase;
import com.foodrescue.order.application.usecase.CustomerOrderUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {
    private final CreateOrderUseCase createOrderUseCase;
    private final CustomerOrderUseCase customerOrderUseCase;

    public OrderController(CreateOrderUseCase createOrderUseCase, CustomerOrderUseCase customerOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
        this.customerOrderUseCase = customerOrderUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) UUID correlationId,
            @RequestBody(required = false) String body) {
        if (body != null && !body.isBlank()) {
            throw com.foodrescue.order.domain.exception.OrderException.validation("Order body must be empty; checkout uses the saved cart");
        }
        return createOrderUseCase.createOrder(user.id(), idempotencyKey, correlationId);
    }

    @GetMapping
    public List<OrderResponse> getMyOrders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status) {
        return customerOrderUseCase.getMyOrders(user.id(), status);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrderById(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return customerOrderUseCase.getOrderById(user.id(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null) ? body.get("reason") : null;
        return customerOrderUseCase.cancelOrder(user.id(), id, reason);
    }
}
