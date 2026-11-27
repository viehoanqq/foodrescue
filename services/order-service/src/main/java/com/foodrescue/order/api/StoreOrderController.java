package com.foodrescue.order.api;

import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.api.dto.OrderPageResponse;
import com.foodrescue.order.application.usecase.CustomerOrderUseCase;
import com.foodrescue.order.application.usecase.StorePickupUseCase;
import com.foodrescue.order.domain.exception.OrderException;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
    public OrderPageResponse getStoreOrders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long storeId = requireStoreId(user);
        return storePickupUseCase.getStoreOrders(storeId, date, status, page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse getStoreOrderDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        Long storeId = requireStoreId(user);
        OrderResponse order = customerOrderUseCase.getOrderById(user.id(), id);
        if (!order.storeId().equals(storeId)) {
            throw OrderException.forbidden("Đơn hàng không thuộc cửa hàng này");
        }
        return order;
    }

    private Long requireStoreId(AuthenticatedUser user) {
        if (user.storeId() == null) {
            throw OrderException.forbidden("Tài khoản chưa thuộc cửa hàng nào");
        }
        return user.storeId();
    }
}
