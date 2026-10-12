package io.roseiot.iam.role;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


interface RoleMapper extends BaseMapper<RoleEntity> {

    @Select("SELECT COUNT(*) FROM user_role WHERE tenant_id = #{tenantId} AND role_id = #{roleId}")
    int hasAssignments(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    @Select("""
            SELECT COUNT(*)
            FROM users u
            JOIN user_role ur ON ur.user_id = u.id AND ur.tenant_id = u.tenant_id
            JOIN "role" r ON r.id = ur.role_id AND r.tenant_id = u.tenant_id
            WHERE u.tenant_id = #{tenantId}
              AND u.status = 'ACTIVE'
              AND EXISTS (SELECT 1 FROM role_resource ra JOIN resource a ON a.id = ra.resource_id
                WHERE ra.role_id = r.id AND ra.tenant_id = u.tenant_id AND a.status = 'ACTIVE'
                  AND a.code = 'tenant.user.manage')
            """)
    int countActiveTenantUsersWithRole(@Param("tenantId") Long tenantId);

}
