package io.roseiot.shared.security;

/** Tenant identity exposed by an authenticated principal to tenant-aware infrastructure. */
public interface TenantPrincipal {
    Long tenantId();
}
