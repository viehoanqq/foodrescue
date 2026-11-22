package com.foodrescue.identity.api;

import com.foodrescue.identity.application.usecase.IdentityService;
import com.foodrescue.identity.domain.model.PageResult;
import com.foodrescue.identity.infrastructure.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {
    private final IdentityService service;

    public AdminUserController(IdentityService service) { this.service = service; }

    @GetMapping("/api/admin/users")
    public PageResult<IdentityController.UserView> users(@RequestParam(required = false) String role,
            @RequestParam(required = false) String status, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = service.search(role, status, keyword, page, size);
        return new PageResult<>(result.content().stream().map(IdentityController.UserView::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @PostMapping("/api/admin/users/{id}/lock")
    public IdentityController.UserView lock(@AuthenticationPrincipal IdentityPrincipal actor, @PathVariable Long id) {
        return IdentityController.UserView.from(service.setLocked(actor.id(), id, true));
    }

    @PostMapping("/api/admin/users/{id}/unlock")
    public IdentityController.UserView unlock(@AuthenticationPrincipal IdentityPrincipal actor, @PathVariable Long id) {
        return IdentityController.UserView.from(service.setLocked(actor.id(), id, false));
    }

    @PostMapping("/api/admin/users/{id}/reset-password")
    public IdentityService.ResetResult reset(@AuthenticationPrincipal IdentityPrincipal actor, @PathVariable Long id) {
        return service.resetPassword(actor.id(), id);
    }

    @GetMapping("/api/admin/audit-logs")
    public PageResult<Map<String, Object>> logs(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.auditLogs(page, size);
    }
}
