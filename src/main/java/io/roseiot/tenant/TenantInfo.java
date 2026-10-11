package io.roseiot.tenant;

public record TenantInfo(Long id, String code, String name, boolean disabled) {

    static TenantInfo from(Tenant tenant) {
        return new TenantInfo(tenant.id(), tenant.code(), tenant.name(), tenant.status() == TenantStatus.DISABLED);
    }
}
