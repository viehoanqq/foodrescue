package com.foodrescue.order;

import com.foodrescue.common.enums.OrderStatus;
import com.foodrescue.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store/orders")
public class StoreOrderController {

    private final OrderService orderService;

    public StoreOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> getStoreOrders(@RequestParam(required = false) OrderStatus status) {
        return orderService.getStoreOrders(CurrentUser.storeId(), status);
    }

    @GetMapping("/{id}")
    public OrderResponse getStoreOrderDetail(@PathVariable Long id) {
        return orderService.getStoreOrderDetail(CurrentUser.storeId(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable Long id,
            @Valid @RequestBody CancelOrderRequest req) {
        return orderService.cancelStoreOrder(CurrentUser.id(), CurrentUser.storeId(), id, req.reason());
    }
}
