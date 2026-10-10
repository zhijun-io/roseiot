package io.roseiot.iam.persistence;

import com.baomidou.mybatisplus.annotation.TableName;

import java.util.UUID;

/** Assignment of a catalogue ACTION to a role inside one tenant. */
@TableName("role_resource")
public class RoleResource {

    private UUID tenantId;
    private UUID roleId;
    private UUID resourceId;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getRoleId() { return roleId; }
    public void setRoleId(UUID roleId) { this.roleId = roleId; }
    public UUID getResourceId() { return resourceId; }
    public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }
}
