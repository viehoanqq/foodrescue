package com.foodrescue.catalog.api;

import com.foodrescue.buildingblocks.http.InternalKeyVerifier;
import com.foodrescue.catalog.application.usecase.BatchStoreUseCase;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/batches")
public class InternalBatchController {
    private final BatchStoreUseCase batches;
    private final InternalKeyVerifier verifier;

    public InternalBatchController(BatchStoreUseCase batches, @Value("${app.internal-api-key}") String key) {
        this.batches = batches;
        this.verifier = new InternalKeyVerifier(key);
    }

    @PostMapping("/checkout-store")
    public CheckoutStoreResponse checkoutStore(@RequestHeader("X-Internal-Key") String key,
                                                @RequestBody CheckoutStoreRequest request) {
        verifier.verify(key);
        return new CheckoutStoreResponse(batches.requireSingleStore(request.batchIds()));
    }

    public record CheckoutStoreRequest(List<Long> batchIds) {}
    public record CheckoutStoreResponse(Long storeId) {}
}
