package com.foodrescue.store.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.store.application.usecase.RegisterStoreUseCase;
import com.foodrescue.store.application.usecase.ReadStoreUseCase;
import com.foodrescue.store.application.port.out.IImageStorage.Upload;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreRegistration;
import com.foodrescue.store.domain.model.StoreView;
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
    private final ObjectMapper mapper;

    public StoreController(RegisterStoreUseCase registerStore, ReadStoreUseCase readStore, ObjectMapper mapper) {
        this.registerStore = registerStore;
        this.readStore = readStore;
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

}

