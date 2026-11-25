package com.foodrescue.order.api;

import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.application.usecase.CustomerOrderUseCase;
import com.foodrescue.order.application.usecase.StorePickupUseCase;
import com.foodrescue.order.domain.exception.OrderException;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store/orders")
@SecurityRequirement(name = "bearerAuth")
public class StoreOrderController {
    private final StorePickupUseCase storePickupUseCase;
    private final CustomerOrderUseCase customerOrderUseCase;

    public StoreOrderController(StorePickupUseCase storePickupUseCase, CustomerOrderUseCase customerOrderUseCase) {
        this.storePickupUseCase = storePickupUseCase;
        this.customerOrderUseCase = customerOrderUseCase;
    }

    @GetMapping
    public List<OrderResponse> getStoreOrders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status) {
        Long storeId = user.storeId() != null ? user.storeId() : 1L;
        return storePickupUseCase.getStoreOrders(storeId, status);
    }

    @GetMapping("/{id}")
    public OrderResponse getStoreOrderDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        Long storeId = user.storeId() != null ? user.storeId() : 1L;
        OrderResponse order = customerOrderUseCase.getOrderById(user.id(), id);
        if (!order.storeId().equals(storeId)) {
            throw OrderException.forbidden("Đơn hàng không thuộc cửa hàng này");
        }
        return order;
    }
}
