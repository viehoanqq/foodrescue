package com.foodrescue.store.api;

import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.store.application.port.out.IStaffIdentityPort.CreatedStaff;
import com.foodrescue.store.application.port.out.IStaffIdentityPort.NewStaff;
import com.foodrescue.store.application.port.out.IStaffIdentityPort.StaffPage;
import com.foodrescue.store.application.port.out.IStaffIdentityPort.StaffStatus;
import com.foodrescue.store.application.usecase.StoreStaffUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class StoreStaffController {
    private final StoreStaffUseCase staffUseCase;

    public StoreStaffController(StoreStaffUseCase staffUseCase) { this.staffUseCase = staffUseCase; }

    @PostMapping("/api/store/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse create(@AuthenticationPrincipal AuthenticatedUser owner,
                                @Valid @RequestBody CreateStaffRequest request) {
        CreatedStaff staff = staffUseCase.execute(owner.id(),
                new NewStaff(request.email(), request.fullName(), request.phone()));
        return new StaffResponse(staff.userId(), staff.email(), staff.fullName(), staff.phone(), staff.role(),
                staff.storeId(), staff.status(), staff.mustChangePassword(), staff.temporaryPassword());
    }

    @GetMapping("/api/store/staff")
    public StaffPage list(@AuthenticationPrincipal AuthenticatedUser owner,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return staffUseCase.list(owner.id(), page, size);
    }

    @PostMapping("/api/store/staff/{id}/lock")
    public StaffStatus lock(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable Long id) {
        return staffUseCase.setLocked(owner.id(), id, true);
    }

    @PostMapping("/api/store/staff/{id}/unlock")
    public StaffStatus unlock(@AuthenticationPrincipal AuthenticatedUser owner, @PathVariable Long id) {
        return staffUseCase.setLocked(owner.id(), id, false);
    }

    public record CreateStaffRequest(@NotBlank @Email @Size(max = 100) String email,
                                     @NotBlank @Size(max = 100) String fullName,
                                     @NotBlank @Size(max = 20) String phone) {}

    public record StaffResponse(Long userId, String email, String fullName, String phone, String role,
                                Long storeId, String status, boolean mustChangePassword,
                                String temporaryPassword) {}
}
