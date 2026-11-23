package com.foodrescue.store.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.store.application.usecase.RegisterStoreUseCase;
import com.foodrescue.store.application.port.out.IImageStorage.Upload;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.StoreRegistration;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class StoreRegistrationController {
    private final RegisterStoreUseCase useCase;
    private final ObjectMapper mapper;

    public StoreRegistrationController(RegisterStoreUseCase useCase, ObjectMapper mapper) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

    @PostMapping(value = "/api/store/registration", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterStoreUseCase.Result register(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("data") String json, @RequestPart("license") MultipartFile license,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        StoreRegistration request;
        try { request = mapper.readValue(json, StoreRegistration.class); }
        catch (IOException ex) { throw new StoreException(400, "INVALID_DATA", "Dữ liệu đăng ký không hợp lệ"); }
        return useCase.execute(user.id(), request, upload(license), upload(image));
    }

    private Upload upload(MultipartFile file) {
        if (file == null) return null;
        try { return new Upload(file.getBytes(), file.getContentType()); }
        catch (IOException ex) { throw new StoreException(400, "INVALID_FILE", "Không thể đọc ảnh tải lên"); }
    }

}
