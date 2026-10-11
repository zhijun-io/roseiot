package io.roseiot.tenant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.roseiot.shared.webmvc.CursorPage;

@RestController
@RequestMapping("/tenants")
class TenantController {

    private final TenantService tenantService;

    TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenant.read')")
    CursorPage<TenantInfo> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        CursorPage<Tenant> page = tenantService.list(cursor, limit);
        return new CursorPage<>(page.items().stream().map(TenantInfo::from).toList(), page.nextCursor());
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('tenant.read')")
    TenantInfo get(@PathVariable Long tenantId) {
        return TenantInfo.from(tenantService.get(tenantId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('tenant.create')")
    TenantInfo create(@Valid @RequestBody CreateTenantRequest request) {
        return TenantInfo.from(tenantService.create(request.name()));
    }

    @PutMapping("/{tenantId}")
    @PreAuthorize("(#request.disabled == null or (#request.disabled ? hasAuthority('tenant.disable')"
            + " : hasAuthority('tenant.enable'))) and (#request.name == null or #request.name.isBlank()"
            + " or hasAuthority('tenant.create'))")
    TenantInfo update(
            @PathVariable Long tenantId,
            @Valid @RequestBody UpdateTenantRequest request) {
        return TenantInfo.from(tenantService.update(tenantId, request.name(), request.disabled()));
    }

    record CreateTenantRequest(@NotBlank @Size(max = 255) String name) {
    }

    record UpdateTenantRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull Boolean disabled) {
    }
}
