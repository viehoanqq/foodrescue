package com.foodrescue.order.api;

import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.api.dto.PageResponse;
import com.foodrescue.order.application.usecase.StorePickupUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store/orders")
@SecurityRequirement(name = "bearerAuth")
public class StoreOrderController {
    private final StorePickupUseCase storePickupUseCase;

    public StoreOrderController(StorePickupUseCase storePickupUseCase) {
        this.storePickupUseCase = storePickupUseCase;
    }

    @GetMapping
    public PageResponse<OrderResponse> getStoreOrders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long storeId = user.storeId() != null ? user.storeId() : 1L;
        return storePickupUseCase.getStoreOrders(storeId, status, date, code, page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse getStoreOrderDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        Long storeId = user.storeId() != null ? user.storeId() : 1L;
        return storePickupUseCase.getStoreOrderDetail(storeId, id);
    }
}
