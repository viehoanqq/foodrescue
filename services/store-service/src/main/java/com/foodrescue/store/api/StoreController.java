package com.foodrescue.store.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.store.application.usecase.RegisterStoreUseCase;
import com.foodrescue.store.application.usecase.ReadStoreUseCase;
import com.foodrescue.store.application.usecase.ReviewStoreUseCase;
import com.foodrescue.store.application.usecase.StoreReviewUseCase;
import com.foodrescue.store.application.port.out.IImageStorage.Upload;
import com.foodrescue.store.application.port.out.IImageStorage;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreRegistration;
import com.foodrescue.store.domain.model.StoreView;
import com.foodrescue.store.domain.model.ReviewView;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class StoreController {
    private final RegisterStoreUseCase registerStore;
    private final ReadStoreUseCase readStore;
    private final ReviewStoreUseCase reviewStore;
    private final StoreReviewUseCase storeReviews;
    private final IImageStorage images;
    private final ObjectMapper mapper;

    public StoreController(RegisterStoreUseCase registerStore, ReadStoreUseCase readStore,
                           ReviewStoreUseCase reviewStore, StoreReviewUseCase storeReviews,
                           IImageStorage images, ObjectMapper mapper) {
        this.registerStore = registerStore;
        this.readStore = readStore;
        this.reviewStore = reviewStore;
        this.storeReviews = storeReviews;
        this.images = images;
        this.mapper = mapper;
    }

    @PostMapping(value = "/api/store/registration", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterStoreUseCase.Result register(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("data") String json, @RequestPart("license") MultipartFile license,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        StoreRegistration request;
        try { request = mapper.readValue(json, StoreRegistration.class); }
        catch (IOException ex) { throw new StoreException(400, "INVALID_DATA", "Dữ liệu đăng ký không hợp lệ"); }
        return registerStore.execute(user.id(), request, upload(license), upload(image));
    }

    private Upload upload(MultipartFile file) {
        if (file == null) return null;
        try { return new Upload(file.getBytes(), file.getContentType()); }
        catch (IOException ex) { throw new StoreException(400, "INVALID_FILE", "Không thể đọc ảnh tải lên"); }
    }

    @GetMapping("/api/store/registration")
    @SecurityRequirement(name = "bearerAuth")
    public StoreView.Registration registration(@AuthenticationPrincipal AuthenticatedUser user) {
        return readStore.registration(user.id());
    }

    @GetMapping("/api/store/profile")
    @SecurityRequirement(name = "bearerAuth")
    public StoreView.Profile profile(@AuthenticationPrincipal AuthenticatedUser owner) {
        return readStore.profile(owner.id());
    }

    @GetMapping("/api/stores")
    public StoreView.Page list(@RequestParam(required = false) BigDecimal lat,
                               @RequestParam(required = false) BigDecimal lon,
                               @RequestParam(required = false) BigDecimal radiusKm,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        return readStore.list(lat, lon, radiusKm, page, size);
    }

    @GetMapping("/api/stores/{id}")
    public StoreView.Detail detail(@PathVariable Long id) { return readStore.detail(id); }

    @PostMapping("/api/stores/{id}/reviews")
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewView.Item createReview(@AuthenticationPrincipal AuthenticatedUser customer,
                                         @PathVariable Long id, @RequestBody ReviewView.Create request) {
        return storeReviews.create(id, customer.id(), request);
    }

    @GetMapping("/api/stores/{id}/reviews")
    public ReviewView.Page reviews(@PathVariable Long id,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return storeReviews.list(id, page, size);
    }

    @GetMapping("/api/admin/stores")
    @SecurityRequirement(name = "bearerAuth")
    public StoreView.AdminPage adminList(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return readStore.adminList(status, page, size);
    }

    @GetMapping("/api/admin/stores/{id}")
    @SecurityRequirement(name = "bearerAuth")
    public StoreView.Admin adminDetail(@PathVariable Long id) { return readStore.adminDetail(id); }

    @GetMapping("/api/admin/stores/{id}/license")
    @SecurityRequirement(name = "bearerAuth")
    public org.springframework.http.ResponseEntity<byte[]> license(@PathVariable Long id) {
        String url = readStore.adminDetail(id).licenseImageUrl();
        String type = url != null && url.endsWith(".png") ? "image/png"
                : url != null && url.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control", "no-store")
                .contentType(MediaType.parseMediaType(type)).body(images.readLicense(url));
    }

    @PostMapping("/api/admin/stores/{id}/approve")
    @SecurityRequirement(name = "bearerAuth")
    public StoreView.Admin approve(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id, @RequestBody(required = false) CommissionRequest request) {
        return reviewStore.approve(id, admin.id(), request == null ? null : request.commissionRate());
    }

    @PostMapping("/api/admin/stores/{id}/reject")
    @SecurityRequirement(name = "bearerAuth")
    public StoreView.Admin reject(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id, @RequestBody RejectRequest request) {
        return reviewStore.reject(id, admin.id(), request.reason());
    }

    @PostMapping("/api/admin/stores/{id}/lock")
    @SecurityRequirement(name = "bearerAuth")
    public StoreView.Admin lock(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id, @RequestBody RejectRequest request) {
        return reviewStore.setLocked(id, admin.id(), true, request.reason());
    }

    @PostMapping("/api/admin/stores/{id}/unlock")
    @SecurityRequirement(name = "bearerAuth")
    public StoreView.Admin unlock(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id) {
        return reviewStore.setLocked(id, admin.id(), false, null);
    }

    @PutMapping("/api/admin/stores/{id}/commission")
    @SecurityRequirement(name = "bearerAuth")
    public StoreView.Admin commission(@AuthenticationPrincipal AuthenticatedUser admin,
            @PathVariable Long id, @RequestBody CommissionRequest request) {
        return reviewStore.commission(id, admin.id(), request.commissionRate());
    }

    public record CommissionRequest(BigDecimal commissionRate) {}
    public record RejectRequest(String reason) {}

}

