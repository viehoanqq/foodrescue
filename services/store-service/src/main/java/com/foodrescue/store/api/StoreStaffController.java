package com.foodrescue.store.api;

import com.foodrescue.buildingblocks.security.AuthenticatedUser;
import com.foodrescue.store.application.port.out.StaffIdentityPort.CreatedStaff;
import com.foodrescue.store.application.port.out.StaffIdentityPort.NewStaff;
import com.foodrescue.store.application.usecase.CreateStaffUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class StoreStaffController {
    private final CreateStaffUseCase createStaff;

    public StoreStaffController(CreateStaffUseCase createStaff) { this.createStaff = createStaff; }

    @PostMapping("/api/store/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse create(@AuthenticationPrincipal AuthenticatedUser owner,
                                @Valid @RequestBody CreateStaffRequest request) {
        CreatedStaff staff = createStaff.execute(owner.id(),
                new NewStaff(request.email(), request.fullName(), request.phone()));
        return new StaffResponse(staff.userId(), staff.email(), staff.fullName(), staff.phone(), staff.role(),
                staff.storeId(), staff.status(), staff.mustChangePassword(), staff.temporaryPassword());
    }

    public record CreateStaffRequest(@NotBlank @Email @Size(max = 100) String email,
                                     @NotBlank @Size(max = 100) String fullName,
                                     @NotBlank @Size(max = 20) String phone) {}

    public record StaffResponse(Long userId, String email, String fullName, String phone, String role,
                                Long storeId, String status, boolean mustChangePassword,
                                String temporaryPassword) {}
}
