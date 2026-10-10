package io.roseiot.iam.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.roseiot.iam.domain.ActionCode;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

public interface UserRoleMapper extends BaseMapper<UserRole> {

    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT p.code
            FROM user_role ur
            JOIN users u ON u.id = ur.user_id
                AND ur.tenant_id = u.tenant_id
            JOIN role r ON r.id = ur.role_id AND r.tenant_id = u.tenant_id
            JOIN role_resource rp ON rp.role_id = ur.role_id AND rp.tenant_id = u.tenant_id
            JOIN resource p ON p.id = rp.resource_id AND p.type = 'ACTION' AND p.status = 'ACTIVE'
            JOIN tenant default_tenant ON default_tenant.id = p.tenant_id AND default_tenant.slug = 'default'
              AND (p.code NOT IN (""" + ActionCode.PLATFORM_ONLY_SQL_CODES + """
              ) OR (r.tenant_id = default_tenant.id
                  AND r.code = 'TENANT_ADMIN' AND r.built_in))
            WHERE ur.user_id = #{userId}
            """)
    List<String> listPermissionCodesForUser(@Param("userId") UUID userId);

    @Select("""
            SELECT COUNT(*)
            FROM users u
            JOIN user_role ur ON ur.user_id = u.id AND ur.tenant_id = u.tenant_id
            JOIN "role" r ON r.id = ur.role_id AND r.tenant_id = u.tenant_id
            WHERE u.tenant_id = #{tenantId}
              AND u.status = 'ACTIVE'
              AND EXISTS (SELECT 1 FROM role_resource ra JOIN resource a ON a.id = ra.resource_id
                WHERE ra.role_id = r.id AND ra.tenant_id = u.tenant_id AND a.status = 'ACTIVE' AND a.code = '
            """ + ActionCode.TENANT_USER_MANAGE + """
            ')
              AND EXISTS (SELECT 1 FROM role_resource ra JOIN resource a ON a.id = ra.resource_id
                WHERE ra.role_id = r.id AND ra.tenant_id = u.tenant_id AND a.status = 'ACTIVE' AND a.code = '
            """ + ActionCode.TENANT_USER_INVITE + """
            ')
            """)
    int countActiveTenantUsersWithRole(@Param("tenantId") UUID tenantId, @Param("roleCode") String roleCode);

    @Select("""
            SELECT u.id, u.email, u.status, r.id AS role_id
            FROM users u
            JOIN user_role ur ON ur.user_id = u.id AND ur.tenant_id = u.tenant_id
            JOIN "role" r ON r.id = ur.role_id AND r.tenant_id = u.tenant_id
            WHERE u.tenant_id = #{tenantId}
            ORDER BY u.created_at
            """)
    List<TenantUserRow> listTenantUsers(UUID tenantId);

    default void insertAssignment(UUID id, UUID userId, UUID roleId, UUID tenantId) {
        UserRole assignment = new UserRole();
        assignment.setId(id);
        assignment.setUserId(userId);
        assignment.setRoleId(roleId);
        assignment.setTenantId(tenantId);
        insert(assignment);
    }
}
