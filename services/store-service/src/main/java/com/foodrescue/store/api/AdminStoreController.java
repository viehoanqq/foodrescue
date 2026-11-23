package com.foodrescue.store.api;

import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.store.application.port.out.IImageStorage;
import com.foodrescue.store.application.usecase.ReadAdminStoreUseCase;
import com.foodrescue.store.application.usecase.AdminStoreUseCase;
import com.foodrescue.store.domain.model.StoreView;
import com.foodrescue.store.domain.model.StoreProfileUpdate;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Quản trị cửa hàng", description = "Duyệt hồ sơ, xem giấy phép và quản lý trạng thái cửa hàng")
public class AdminStoreController {
    private final ReadAdminStoreUseCase readStore;
    private final AdminStoreUseCase adminStore;
    private final IImageStorage images;

    public AdminStoreController(ReadAdminStoreUseCase readStore, AdminStoreUseCase adminStore,
                                IImageStorage images) {
        this.readStore = readStore;
        this.adminStore = adminStore;
        this.images = images;
    }

    @GetMapping("/api/admin/stores")
    public StoreView.AdminPage list(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return readStore.list(status, page, size);
    }

    @GetMapping("/api/admin/stores/{id}")
    public StoreView.Admin detail(@PathVariable Long id) { return readStore.detail(id); }

    @GetMapping("/api/admin/stores/{id}/license")
    public ResponseEntity<byte[]> license(@PathVariable Long id) {
        String url = readStore.detail(id).licenseImageUrl();
        String type = url != null && url.endsWith(".png") ? "image/png"
                : url != null && url.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .contentType(MediaType.parseMediaType(type)).body(images.readLicense(url));
    }

    @PostMapping("/api/admin/stores/{id}/approve")
    public StoreView.Admin approve(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id, @RequestBody(required = false) CommissionRequest request) {
        return adminStore.approve(id, admin.id(), request == null ? null : request.commissionRate());
    }

    @PostMapping("/api/admin/stores/{id}/reject")
    public StoreView.Admin reject(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id, @RequestBody RejectRequest request) {
        return adminStore.reject(id, admin.id(), request.reason());
    }

    @PostMapping("/api/admin/stores/{id}/lock")
    public StoreView.Admin lock(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id, @RequestBody RejectRequest request) {
        return adminStore.setLocked(id, admin.id(), true, request.reason());
    }

    @PostMapping("/api/admin/stores/{id}/unlock")
    public StoreView.Admin unlock(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id) {
        return adminStore.setLocked(id, admin.id(), false, null);
    }

    @PutMapping("/api/admin/stores/{id}/commission")
    public StoreView.Admin commission(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id, @RequestBody CommissionRequest request) {
        return adminStore.commission(id, admin.id(), request.commissionRate());
    }

    @PutMapping("/api/admin/stores/{id}")
    public StoreView.Admin update(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id, @RequestBody StoreProfileUpdate request) {
        return adminStore.updateProfile(id, admin.id(), request);
    }

    public record CommissionRequest(BigDecimal commissionRate) {}
    public record RejectRequest(String reason) {}
}
