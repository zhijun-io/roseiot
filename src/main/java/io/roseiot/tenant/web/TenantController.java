package io.roseiot.tenant.web;

import io.roseiot.iam.domain.ActionCode;
import io.roseiot.tenant.application.TenantService;
import io.roseiot.tenant.persistence.Tenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('" + ActionCode.TENANT_CREATE + "')")
    CreatedTenantResponse create(@Valid @RequestBody CreateTenantRequest request) {
        return CreatedTenantResponse.from(tenantService.create(
                request.name(), request.slug(), request.initialAdminEmail()));
    }

    @PutMapping("/{tenantId}")
    @PreAuthorize("(#request.disabled == null or (#request.disabled ? hasAuthority('"
            + ActionCode.TENANT_DISABLE + "') : hasAuthority('" + ActionCode.TENANT_ENABLE
            + "'))) and (#request.name == null or #request.name.isBlank() or hasAuthority('"
            + ActionCode.TENANT_CREATE + "'))")
    TenantResponse update(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantRequest request) {
        return TenantResponse.from(tenantService.updateTenant(tenantId, request.name(), request.disabled()));
    }

    public record CreateTenantRequest(
            @NotBlank @Size(max = 255) String name,
            @Pattern(regexp = "^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])$") String slug,
            @NotBlank @Email String initialAdminEmail) {}

    public record UpdateTenantRequest(
            @NotBlank @Size(max = 255) String name,
            @NotNull Boolean disabled) {}

    public record CreatedTenantResponse(UUID id, String name, String slug) {

        static CreatedTenantResponse from(Tenant tenant) {
            return new CreatedTenantResponse(tenant.getId(), tenant.getName(), tenant.getSlug());
        }
    }

    public record TenantResponse(UUID id, String name, String slug, boolean disabled) {

        static TenantResponse from(Tenant tenant) {
            return new TenantResponse(
                    tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getDisabledAt() != null);
        }
    }
}
