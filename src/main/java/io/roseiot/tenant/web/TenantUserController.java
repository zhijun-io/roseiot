package io.roseiot.tenant.web;

import io.roseiot.identity.infra.security.MethodSecuritySpel;
import io.roseiot.identity.infra.security.SecurityUser;
import io.roseiot.tenant.application.TenantUserService;
import io.roseiot.tenant.application.TenantUserService.TenantUserSummary;
import io.roseiot.identity.application.RoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/tenants/users")
public class TenantUserController {

    private final TenantUserService userManagementService;

    public TenantUserController(TenantUserService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize(MethodSecuritySpel.TENANT_USER_MANAGE)
    List<TenantUserResponse> list(@AuthenticationPrincipal SecurityUser user) {
        return userManagementService.listUsers(user.requireTenantId()).stream()
                .map(TenantUserResponse::from)
                .toList();
    }

    @PatchMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MethodSecuritySpel.TENANT_USER_MANAGE)
    void update(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateTenantUserRequest request) {
        userManagementService.updateUser(
                user.requireTenantId(), userId, request.disabled(), request.targetRole());
    }

    public record UpdateTenantUserRequest(
            Boolean disabled,
            @Pattern(regexp = RoleService.TENANT_ADMIN + "|" + RoleService.TENANT_USER) String targetRole) {

        @AssertTrue(message = "At least one of disabled or targetRole must be provided")
        boolean isChangeRequested() {
            return disabled != null || (targetRole != null && !targetRole.isBlank());
        }
    }

    public record TenantUserResponse(UUID id, String email, String role, boolean disabled) {

        static TenantUserResponse from(TenantUserSummary summary) {
            return new TenantUserResponse(summary.id(), summary.email(), summary.role(), summary.disabled());
        }
    }
}
