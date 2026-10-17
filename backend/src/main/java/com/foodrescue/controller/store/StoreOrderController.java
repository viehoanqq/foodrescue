package com.foodrescue.controller.store;

import com.foodrescue.dto.request.CancelOrderRequest;
import com.foodrescue.dto.response.OrderResponse;
import com.foodrescue.entity.enums.OrderStatus;
import com.foodrescue.security.CurrentUser;
import com.foodrescue.service.OrderService;
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
