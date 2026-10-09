package io.roseiot.tenant.application;

public final class TenantRoles {

    public static final String TENANT_ADMIN = TenantService.TARGET_ROLE_TENANT_ADMIN;
    public static final String TENANT_USER = "TenantUser";

    private TenantRoles() {}

    public static void requireAssigneeRole(String targetRole) {
        if (targetRole == null) {
            throw new IllegalArgumentException("targetRole is required");
        }
        requireAssigneeRoleCode(targetRole.trim());
    }

    public static void requireAssigneeRoleCode(String targetRole) {
        if (!TENANT_ADMIN.equals(targetRole) && !TENANT_USER.equals(targetRole)) {
            throw new IllegalArgumentException("Invalid targetRole");
        }
    }
}
