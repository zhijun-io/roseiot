package io.roseiot.iam.role;


/** Public role model. RoleEntity and mapper types remain internal to the role feature. */
public record Role(Long id, Long tenantId, String name) {

    static Role from(RoleEntity entity) {
        return new Role(entity.getId(), entity.getTenantId(), entity.getName());
    }
}
