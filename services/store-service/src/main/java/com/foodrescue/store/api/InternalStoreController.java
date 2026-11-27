package com.foodrescue.store.api;

import com.foodrescue.buildingblocks.http.InternalKeyVerifier;
import com.foodrescue.store.application.usecase.ReadInternalStoreUseCase;
import com.foodrescue.store.domain.model.StoreInternalView;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Store nội bộ", description = "Chính sách bán, tài khoản chi trả và số lượng cửa hàng")
public class InternalStoreController {
    private final ReadInternalStoreUseCase stores;
    private final InternalKeyVerifier sharedKey;
    private final InternalKeyVerifier financeKey;

    public InternalStoreController(ReadInternalStoreUseCase stores,
            @Value("${app.internal-api-key}") String sharedKey,
            @Value("${app.finance-store-api-key}") String financeKey) {
        this.stores = stores;
        this.sharedKey = new InternalKeyVerifier(sharedKey);
        this.financeKey = new InternalKeyVerifier(financeKey);
    }

    @GetMapping("/internal/stores/{id}/checkout-policy")
    public StoreInternalView.CheckoutPolicy checkoutPolicy(
            @RequestHeader("X-Internal-Key") String key, @PathVariable Long id) {
        sharedKey.verify(key);
        return stores.checkoutPolicy(id);
    }

    @GetMapping("/internal/stores/{id}/payout-account")
    public ResponseEntity<StoreInternalView.PayoutAccount> payoutAccount(
            @RequestHeader("X-Internal-Key") String key, @PathVariable Long id) {
        financeKey.verify(key);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(stores.payoutAccount(id));
    }

    @GetMapping("/internal/stores/count")
    public StoreInternalView.Counts count(@RequestHeader("X-Internal-Key") String key) {
        sharedKey.verify(key);
        return stores.count();
    }
}
