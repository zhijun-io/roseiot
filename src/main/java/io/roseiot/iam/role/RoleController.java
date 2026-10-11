package io.roseiot.iam.role;

import io.roseiot.shared.security.SecurityUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
class RoleController {

    private final RoleService roleService;

    RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    Page<RoleInfo> list(
            @AuthenticationPrincipal SecurityUser user,
            @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return roleService.list(user.requireTenantId(), pageable);
    }

    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    RoleInfo get(@AuthenticationPrincipal SecurityUser user, @PathVariable Long roleId) {
        return roleService.get(user.requireTenantId(), roleId);
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    RoleInfo create(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody RoleRequest request) {
        return roleService.create(user.requireTenantId(), request.name(), Set.copyOf(request.actionCodes()));
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    RoleInfo update(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long roleId,
            @Valid @RequestBody RoleRequest request) {
        return roleService.update(user.requireTenantId(), roleId, request.name(), Set.copyOf(request.actionCodes()));
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    void delete(@AuthenticationPrincipal SecurityUser user, @PathVariable Long roleId) {
        roleService.delete(user.requireTenantId(), roleId);
    }

    record RoleRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 128) String> actionCodes) {}
}
