package io.roseiot.tenant.web;

import io.roseiot.identity.application.AuthorizationService;
import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.tenant.application.TenantService;
import io.roseiot.tenant.persistence.Tenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final AuthorizationService authorizationService;

    public TenantController(TenantService tenantService, AuthorizationService authorizationService) {
        this.tenantService = tenantService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    ResponseEntity<CreatedTenantResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTenantRequest request) {
        requireSystemPermission(jwt, IdentityJwtClaims.PERMISSION_TENANT_CREATE);
        Tenant tenant = tenantService.create(
                request.name(), request.slug(), request.initialAdminEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedTenantResponse(tenant.getId(), tenant.getName(), tenant.getSlug()));
    }

    @PatchMapping("/{tenantId}")
    TenantResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantRequest request) {
        if (request.disabled() != null) {
            requireSystemPermission(jwt, request.disabled()
                    ? IdentityJwtClaims.PERMISSION_TENANT_DISABLE
                    : IdentityJwtClaims.PERMISSION_TENANT_ENABLE);
        }
        if (request.name() != null && !request.name().isBlank()) {
            requireSystemPermission(jwt, IdentityJwtClaims.PERMISSION_TENANT_CREATE);
        }
        Tenant tenant = tenantService.updateTenant(tenantId, request.name(), request.disabled());
        return new TenantResponse(
                tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getDisabledAt() != null);
    }

    private void requireSystemPermission(Jwt jwt, String permissionKey) {
        if (!IdentityJwtClaims.CONTEXT_SYSTEM.equals(jwt.getClaimAsString(IdentityJwtClaims.CONTEXT))) {
            throw new AccessDeniedException("Forbidden");
        }
        long azv = readAuthorizationVersion(jwt);
        UUID userId = UUID.fromString(jwt.getSubject());
        if (!authorizationService.hasPermission(userId, azv, permissionKey)) {
            throw new AccessDeniedException("Forbidden");
        }
    }

    private static long readAuthorizationVersion(Jwt jwt) {
        Object claim = jwt.getClaim(IdentityJwtClaims.AUTHORIZATION_VERSION);
        if (claim instanceof Number number) {
            return number.longValue();
        }
        throw new AccessDeniedException("Forbidden");
    }

    public record CreateTenantRequest(
            @NotBlank @Size(max = 255) String name,
            @Pattern(regexp = "^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])$") String slug,
            @NotBlank @Email String initialAdminEmail) {}

    public record UpdateTenantRequest(
            @Size(max = 255) String name,
            Boolean disabled) {}

    public record CreatedTenantResponse(UUID id, String name, String slug) {}

    public record TenantResponse(UUID id, String name, String slug, boolean disabled) {}
}
