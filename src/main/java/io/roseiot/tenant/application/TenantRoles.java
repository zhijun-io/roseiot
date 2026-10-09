package io.roseiot.tenant.application;

public final class TenantRoles {

    public static final String TENANT_ADMIN = "TenantAdmin";
    public static final String TENANT_USER = "TenantUser";

    private TenantRoles() {}

    public static void requireAssigneeRole(String targetRole) {
        if (targetRole == null) {
            throw new IllegalArgumentException("targetRole is required");
        }
        String role = targetRole.trim();
        if (!TENANT_ADMIN.equals(role) && !TENANT_USER.equals(role)) {
            throw new IllegalArgumentException("Invalid targetRole");
        }
    }
}
