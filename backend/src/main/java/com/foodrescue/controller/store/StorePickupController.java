package com.foodrescue.controller.store;

import com.foodrescue.dto.request.HandoverRequest;
import com.foodrescue.dto.response.OrderResponse;
import com.foodrescue.dto.response.PickupLookupResponse;
import com.foodrescue.security.CurrentUser;
import com.foodrescue.service.PickupService;
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
