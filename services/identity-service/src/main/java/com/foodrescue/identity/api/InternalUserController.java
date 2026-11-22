package com.foodrescue.identity.api;

import com.foodrescue.buildingblocks.http.InternalKeyVerifier;
import com.foodrescue.identity.application.usecase.IdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalUserController {
    private final IdentityService service;
    private final InternalKeyVerifier verifier;

    public InternalUserController(IdentityService service, @Value("${app.internal-api-key}") String key) {
        this.service = service;
        this.verifier = new InternalKeyVerifier(key);
    }

    @GetMapping("/internal/users/{id}/status")
    public UserStatus status(@RequestHeader("X-Internal-Key") String key, @PathVariable Long id) {
        verifier.verify(key);
        var user = service.find(id);
        return new UserStatus(user.id(), user.status(), user.role(), user.fullName(), user.storeId(),
                user.tokenVersion(), user.mustChangePassword());
    }

    @GetMapping("/internal/users/count")
    public IdentityService.UserCounts count(@RequestHeader("X-Internal-Key") String key) {
        verifier.verify(key);
        return service.countUsers();
    }

    @PostMapping("/internal/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse createStaff(@RequestHeader("X-Internal-Key") String key,
                                     @Valid @RequestBody CreateStaffRequest request) {
        verifier.verify(key);
        var result = service.createStaff(request.actorId(), request.storeId(), request.email(),
                request.fullName(), request.phone());
        var user = result.user();
        return new StaffResponse(user.id(), user.email(), user.fullName(), user.phone(), user.role(),
                user.storeId(), user.status(), user.mustChangePassword(), result.temporaryPassword());
    }

    public record UserStatus(Long userId, String status, String role, String fullName, Long storeId,
                             long tokenVersion, boolean mustChangePassword) {}
    public record CreateStaffRequest(@NotNull @Positive Long actorId, @NotNull @Positive Long storeId,
                                     @NotBlank @Email @Size(max = 100) String email,
                                     @NotBlank @Size(max = 100) String fullName,
                                     @NotBlank @Size(max = 20) String phone) {}
    public record StaffResponse(Long userId, String email, String fullName, String phone, String role,
                                Long storeId, String status, boolean mustChangePassword,
                                String temporaryPassword) {}
}
