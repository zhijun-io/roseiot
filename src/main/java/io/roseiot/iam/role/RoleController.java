package io.roseiot.iam.role;

import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
import io.roseiot.shared.security.SecurityUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class RoleController {

    private final RoleService roleService;

    RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    Page<Role> list(
            @AuthenticationPrincipal SecurityUser user,
            @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return roleService.list(user.requireTenantId(), pageable);
    }

    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    Role get(@AuthenticationPrincipal SecurityUser user, @PathVariable Long roleId) {
        return roleService.get(user.requireTenantId(), roleId);
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    Role create(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody RoleRequest request) {
        return roleService.create(user.requireTenantId(), request.name(), actionCodes(request));
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    Role update(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long roleId,
            @Valid @RequestBody RoleRequest request) {
        return roleService.update(user.requireTenantId(), roleId, request.name(), actionCodes(request));
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('tenant.role.manage')")
    void delete(@AuthenticationPrincipal SecurityUser user, @PathVariable Long roleId) {
        roleService.delete(user.requireTenantId(), roleId);
    }

    record RoleRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 128) String> actionCodes) {}

    private static Set<String> actionCodes(RoleRequest request) {
        Set<String> actionCodes = new LinkedHashSet<>(request.actionCodes());
        if (actionCodes.size() != request.actionCodes().size()) {
            throw new BusinessException(ApiErrorCode.VALIDATION_ERROR, "Action codes must be unique");
        }
        return Set.copyOf(actionCodes);
    }
}
