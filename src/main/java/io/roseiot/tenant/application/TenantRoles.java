package io.roseiot.tenant.application;

import io.roseiot.identity.application.RoleService;

public final class TenantRoles {

    private TenantRoles() {}

    public static void requireAssigneeRole(String targetRole) {
        if (targetRole == null) {
            throw new IllegalArgumentException("targetRole is required");
        }
        String role = targetRole.trim();
        if (!RoleService.TENANT_ADMIN.equals(role) && !RoleService.TENANT_USER.equals(role)) {
            throw new IllegalArgumentException("Invalid targetRole");
        }
    }
}
