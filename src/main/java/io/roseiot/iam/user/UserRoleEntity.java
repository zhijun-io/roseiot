package io.roseiot.iam.user;

import com.baomidou.mybatisplus.annotation.TableName;


@TableName("user_role")
class UserRoleEntity {

    private Long userId;

    private Long roleId;

    private Long tenantId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
