package io.roseiot.iam.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

interface UserRoleMapper extends BaseMapper<UserRoleEntity> {

    @Select("""
            SELECT DISTINCT p.code
            FROM user_role ur
            JOIN "user" u ON u.id = ur.user_id
                AND ur.tenant_id = u.tenant_id
            JOIN "role" r ON r.id = ur.role_id AND r.tenant_id = u.tenant_id
            JOIN role_resource rp ON rp.role_id = ur.role_id AND rp.tenant_id = u.tenant_id
            JOIN resource p ON p.id = rp.resource_id AND p.type = 'ACTION' AND p.status = 'ACTIVE'
            JOIN tenant default_tenant ON default_tenant.id = p.tenant_id AND default_tenant.code = 'default'
              AND (p.code NOT IN (
                    'tenant.create', 'tenant.disable', 'tenant.enable', 'tenant.read', 'system.setting.manage')
                  OR r.tenant_id = default_tenant.id)
            WHERE ur.user_id = #{userId}
            """)
    List<String> listPermissionCodesForUser(@Param("userId") Long userId);

    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class),
        @Arg(column = "email", javaType = String.class),
        @Arg(column = "name", javaType = String.class),
        @Arg(column = "role_id", javaType = Long.class),
        @Arg(column = "disabled", javaType = boolean.class)
    })
    @Select("""
            SELECT u.id, u.email, u.name, r.id AS role_id, u.status = 'DISABLED' AS disabled
            FROM "user" u
            JOIN user_role ur ON ur.user_id = u.id AND ur.tenant_id = u.tenant_id
            JOIN "role" r ON r.id = ur.role_id AND r.tenant_id = u.tenant_id
            WHERE u.tenant_id = #{tenantId}
            ORDER BY u.id
            """)
    IPage<UserResponse> list(IPage<?> page, @Param("tenantId") Long tenantId);

    @ConstructorArgs({
        @Arg(column = "id", javaType = Long.class),
        @Arg(column = "email", javaType = String.class),
        @Arg(column = "name", javaType = String.class),
        @Arg(column = "role_id", javaType = Long.class),
        @Arg(column = "disabled", javaType = boolean.class)
    })
    @Select("""
            SELECT u.id, u.email, u.name, r.id AS role_id, u.status = 'DISABLED' AS disabled
            FROM "user" u
            JOIN user_role ur ON ur.user_id = u.id AND ur.tenant_id = u.tenant_id
            JOIN "role" r ON r.id = ur.role_id AND r.tenant_id = u.tenant_id
            WHERE u.tenant_id = #{tenantId} AND u.id = #{userId}
            """)
    UserResponse get(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    default void insertAssignment(Long userId, Long roleId, Long tenantId) {
        insert(UserRoleEntity.from(new UserRole(userId, roleId, tenantId)));
    }
}
