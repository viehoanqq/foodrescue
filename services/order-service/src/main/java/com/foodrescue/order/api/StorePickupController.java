package com.foodrescue.order.api;

import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.order.api.dto.HandoverRequest;
import com.foodrescue.order.api.dto.OrderResponse;
import com.foodrescue.order.api.dto.PickupLookupResponse;
import com.foodrescue.order.application.usecase.StorePickupUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store/pickup")
@SecurityRequirement(name = "bearerAuth")
public class StorePickupController {
    private final StorePickupUseCase pickupUseCase;

    public StorePickupController(StorePickupUseCase pickupUseCase) {
        this.pickupUseCase = pickupUseCase;
    }

    @GetMapping("/lookup")
    public PickupLookupResponse lookup(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String code) {
        Long storeId = user.storeId() != null ? user.storeId() : 1L;
        return pickupUseCase.lookup(storeId, code);
    }

    @GetMapping("/{code}")
    public PickupLookupResponse lookupByPath(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String code) {
        Long storeId = user.storeId() != null ? user.storeId() : 1L;
        return pickupUseCase.lookup(storeId, code);
    }

    @PostMapping("/handover")
    public OrderResponse handover(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody HandoverRequest req) {
        Long storeId = user.storeId() != null ? user.storeId() : 1L;
        return pickupUseCase.handover(storeId, user.id(), req.pickupCode());
    }

    @PostMapping("/{code}/complete")
    public OrderResponse completeByPath(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String code) {
        Long storeId = user.storeId() != null ? user.storeId() : 1L;
        return pickupUseCase.handover(storeId, user.id(), code);
    }
}
