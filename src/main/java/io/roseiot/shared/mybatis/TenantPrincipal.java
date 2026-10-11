package io.roseiot.shared.mybatis;


/** Minimal authenticated-principal contract needed by tenant-aware persistence infrastructure. */
public interface TenantPrincipal {
    Long tenantId();
}
