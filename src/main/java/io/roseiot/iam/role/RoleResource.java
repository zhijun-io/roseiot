package io.roseiot.iam.role;

record RoleResource(Long tenantId, Long roleId, Long resourceId) {

    static RoleResource from(RoleResourceEntity entity) {
        return new RoleResource(entity.getTenantId(), entity.getRoleId(), entity.getResourceId());
    }
}
