package io.roseiot.identity.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.UUID;

@TableName("user_role")
public class UserRole {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID userId;

    private UUID roleId;

    private UUID scopeTenantId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public UUID getScopeTenantId() {
        return scopeTenantId;
    }

    public void setScopeTenantId(UUID scopeTenantId) {
        this.scopeTenantId = scopeTenantId;
    }
}
