package io.roseiot.identity.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

public interface UserRoleMapper extends BaseMapper<UserRole> {

    @Select("""
            SELECT COUNT(*)
            FROM user_role ur
            JOIN role_permission rp ON rp.role_id = ur.role_id
            JOIN permission p ON p.id = rp.permission_id
            WHERE ur.user_id = #{userId} AND p.code = #{permissionKey}
            """)
    Integer countUserPermission(@Param("userId") UUID userId, @Param("permissionKey") String permissionKey);

    default boolean userHasPermission(UUID userId, String permissionKey) {
        Integer count = countUserPermission(userId, permissionKey);
        return count != null && count > 0;
    }

    @Select("""
            SELECT DISTINCT p.code
            FROM user_role ur
            JOIN role_permission rp ON rp.role_id = ur.role_id
            JOIN permission p ON p.id = rp.permission_id
            WHERE ur.user_id = #{userId}
            """)
    List<String> listPermissionCodesForUser(@Param("userId") UUID userId);

    @Select("""
            SELECT DISTINCT r.code
            FROM user_role ur
            JOIN "role" r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            """)
    List<String> listRoleCodesForUser(@Param("userId") UUID userId);

    @Select("""
            SELECT COUNT(*)
            FROM users u
            JOIN user_role ur ON ur.user_id = u.id AND ur.scope_tenant_id = u.tenant_id
            JOIN "role" r ON r.id = ur.role_id
            WHERE u.tenant_id = #{tenantId}
              AND u.disabled_at IS NULL
              AND r.code = #{roleCode}
            """)
    int countActiveTenantUsersWithRole(@Param("tenantId") UUID tenantId, @Param("roleCode") String roleCode);

    @Select("""
            SELECT u.id, u.email, u.disabled_at, r.code AS role_code
            FROM users u
            JOIN user_role ur ON ur.user_id = u.id AND ur.scope_tenant_id = u.tenant_id
            JOIN "role" r ON r.id = ur.role_id
            WHERE u.tenant_id = #{tenantId}
            ORDER BY u.created_at
            """)
    List<TenantUserRow> listTenantUsers(UUID tenantId);

    default UserRole selectFirstByUserId(UUID userId) {
        return selectOne(Wrappers.lambdaQuery(UserRole.class)
                .eq(UserRole::getUserId, userId)
                .last("LIMIT 1"));
    }

    default void replaceRoleInTenantScope(UUID userId, UUID scopeTenantId, UUID roleId) {
        update(
                null,
                Wrappers.lambdaUpdate(UserRole.class)
                        .set(UserRole::getRoleId, roleId)
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getScopeTenantId, scopeTenantId));
    }

    default void insertAssignment(UUID id, UUID userId, UUID roleId, UUID scopeTenantId) {
        UserRole assignment = new UserRole();
        assignment.setId(id);
        assignment.setUserId(userId);
        assignment.setRoleId(roleId);
        assignment.setScopeTenantId(scopeTenantId);
        insert(assignment);
    }
}
