package com.foodrescue.identity.api;

import com.foodrescue.identity.application.usecase.IdentityService;
import com.foodrescue.identity.domain.model.UserAccount;
import com.foodrescue.identity.infrastructure.security.IdentityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityController {
    private final IdentityService service;

    public IdentityController(IdentityService service) { this.service = service; }

    @PostMapping("/api/auth/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        var result = service.register(request.email(), request.password(), request.fullName(), request.phone());
        return new AuthResponse(result.token(), UserView.from(result.user()));
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        var result = service.login(request.email(), request.password());
        return new AuthResponse(result.token(), UserView.from(result.user()));
    }

    @GetMapping("/api/me")
    public UserView me(@AuthenticationPrincipal IdentityPrincipal principal) {
        return UserView.from(service.find(principal.id()));
    }

    @PutMapping("/api/me")
    public UserView update(@AuthenticationPrincipal IdentityPrincipal principal,
                           @Valid @RequestBody ProfileRequest request) {
        return UserView.from(service.updateProfile(principal.id(), request.fullName(), request.phone()));
    }

    @PutMapping("/api/me/password")
    public void changePassword(@AuthenticationPrincipal IdentityPrincipal principal,
                               @Valid @RequestBody PasswordRequest request) {
        service.changePassword(principal.id(), request.oldPassword(), request.newPassword());
    }

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 100) String fullName,
                                  @NotBlank @Size(max = 20) String phone) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record ProfileRequest(@NotBlank @Size(max = 100) String fullName,
                                 @NotBlank @Size(max = 20) String phone) {}
    public record PasswordRequest(@NotBlank String oldPassword,
                                  @NotBlank @Size(min = 8, max = 72) String newPassword) {}
    public record AuthResponse(String token, UserView user) {}
    public record UserView(Long id, String email, String fullName, String phone, String role,
                           Long storeId, String status, boolean mustChangePassword,
                           LocalDateTime createdAt) {
        public static UserView from(UserAccount user) {
            return new UserView(user.id(), user.email(), user.fullName(), user.phone(), user.role(),
                    user.storeId(), user.status(), user.mustChangePassword(), user.createdAt());
        }
    }
}
