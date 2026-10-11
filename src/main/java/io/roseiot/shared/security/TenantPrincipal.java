package io.roseiot.shared.security;


/** Minimal authenticated-principal contract needed by tenant-aware persistence infrastructure. */
public interface TenantPrincipal {
    Long tenantId();
}
