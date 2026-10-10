package io.roseiot.tenant.web;

import io.roseiot.iam.domain.ActionCode;
import io.roseiot.iam.infra.security.SecurityUser;
import io.roseiot.tenant.application.TenantUserService;
import io.roseiot.tenant.application.TenantUserService.TenantUserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tenants/{tenantId}/users")
public class TenantUserController {

    private final TenantUserService userManagementService;

    public TenantUserController(TenantUserService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + ActionCode.TENANT_USER_MANAGE + "')")
    List<TenantUserResponse> list(@AuthenticationPrincipal SecurityUser user, @PathVariable UUID tenantId) {
        requireCurrentTenant(user, tenantId);
        return userManagementService.listUsers(tenantId).stream()
                .map(TenantUserResponse::from)
                .toList();
    }

    @PutMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + ActionCode.TENANT_USER_MANAGE + "')")
    void update(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateTenantUserRequest request) {
        userManagementService.updateUser(
                requireCurrentTenant(user, tenantId), userId, request.disabled(), request.roleId());
    }

    private static UUID requireCurrentTenant(SecurityUser user, UUID tenantId) {
        if (!tenantId.equals(user.requireTenantId())) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant mismatch");
        }
        return tenantId;
    }

    public record UpdateTenantUserRequest(@NotNull Boolean disabled, @NotNull UUID roleId) {}

    public record TenantUserResponse(UUID id, String email, UUID roleId, boolean disabled) {

        static TenantUserResponse from(TenantUserSummary summary) {
            return new TenantUserResponse(summary.id(), summary.email(), summary.roleId(), summary.disabled());
        }
    }

}
