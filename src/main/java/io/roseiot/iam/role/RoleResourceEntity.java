package io.roseiot.iam.role;

import com.baomidou.mybatisplus.annotation.TableName;


/** Assignment of a catalogue ACTION to a role inside one tenant. */
@TableName("role_resource")
class RoleResourceEntity {

    private Long tenantId;
    private Long roleId;
    private Long resourceId;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
}
