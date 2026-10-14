package com.foodrescue.pickup;

import com.foodrescue.common.security.CurrentUser;
import com.foodrescue.order.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store/pickup")
public class StorePickupController {

    private final PickupService pickupService;

    public StorePickupController(PickupService pickupService) {
        this.pickupService = pickupService;
    }

    @GetMapping("/lookup")
    public PickupLookupResponse lookup(@RequestParam String code) {
        return pickupService.lookupByPickupCode(CurrentUser.storeId(), code);
    }

    @PostMapping("/handover")
    public OrderResponse handover(@Valid @RequestBody HandoverRequest req) {
        return pickupService.handoverByPickupCode(CurrentUser.id(), CurrentUser.storeId(), req.pickupCode());
    }
}
