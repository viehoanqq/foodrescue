package com.foodrescue.order;

import com.foodrescue.common.enums.OrderStatus;
import com.foodrescue.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse createOrder(@Valid @RequestBody(required = false) CreateOrderRequest req) {
        return orderService.createOrder(CurrentUser.id(), req);
    }

    @GetMapping
    public List<OrderResponse> getMyOrders(@RequestParam(required = false) OrderStatus status) {
        return orderService.getCustomerOrders(CurrentUser.id(), status);
    }

    @GetMapping("/{id}")
    public OrderResponse getMyOrderDetail(@PathVariable Long id) {
        return orderService.getCustomerOrderDetail(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelOrderRequest req) {
        String reason = req != null ? req.reason() : null;
        return orderService.cancelCustomerOrder(CurrentUser.id(), id, reason);
    }
}
