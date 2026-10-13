package io.roseiot.tenant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
class TenantController {

    private final TenantService tenantService;

    TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenant.read')")
    Page<Tenant> list(@PageableDefault(size = 50, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return tenantService.list(pageable);
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('tenant.read')")
    Tenant get(@PathVariable Long tenantId) {
        return tenantService.get(tenantId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tenant.create')")
    Tenant create(@Valid @RequestBody CreateTenantRequest request) {
        return tenantService.create(request.name());
    }

    @PutMapping("/{tenantId}")
    @PreAuthorize("(#request.disabled == null or (#request.disabled ? hasAuthority('tenant.disable')"
            + " : hasAuthority('tenant.enable'))) and (#request.name == null or #request.name.isBlank()"
            + " or hasAuthority('tenant.create'))")
    Tenant update(@PathVariable Long tenantId, @Valid @RequestBody UpdateTenantRequest request) {
        return tenantService.update(tenantId, request.name(), request.disabled());
    }

    record CreateTenantRequest(@NotBlank @Size(max = 255) String name) {}

    record UpdateTenantRequest(
            @NotBlank @Size(max = 255) String name, @NotNull Boolean disabled) {}
}
