package com.foodrescue.identity.api;

import com.foodrescue.buildingblocks.http.InternalKeyVerifier;
import com.foodrescue.identity.application.usecase.IdentityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
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
        return new UserStatus(user.id(), user.status(), user.role(), user.storeId(),
                user.tokenVersion(), user.mustChangePassword());
    }

    public record UserStatus(Long userId, String status, String role, Long storeId,
                             long tokenVersion, boolean mustChangePassword) {}
}
