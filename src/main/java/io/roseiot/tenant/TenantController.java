package io.roseiot.tenant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;


@RestController
@RequestMapping("/tenants")
class TenantController {

    private final TenantService tenantService;

    TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenant.read')")
    Page<TenantInfo> list(
            @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return tenantService.list(pageable);
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
