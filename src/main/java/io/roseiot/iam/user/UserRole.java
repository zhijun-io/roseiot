package io.roseiot.iam.user;

record UserRole(Long userId, Long roleId, Long tenantId) {

    static UserRole from(UserRoleEntity entity) {
        return new UserRole(entity.getUserId(), entity.getRoleId(), entity.getTenantId());
    }
}
