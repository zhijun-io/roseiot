package io.roseiot.tenant;

/** Public tenant model; persistence details stay inside the tenant feature package. */
public record Tenant(Long id, String code, String name, TenantStatus status) {
    static final Long DEFAULT_TENANT_ID = 1L;

    static Tenant from(TenantEntity entity) {
        return new Tenant(entity.getId(), entity.getCode(), entity.getName(), entity.getStatus());
    }
}
