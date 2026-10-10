package io.roseiot.iam.persistence;

import io.roseiot.iam.domain.UserStatus;

import java.util.UUID;

/** Row shape for tenant-scoped user listing queries. */
public class TenantUserRow {

    private UUID id;
    private String email;
    private UserStatus status;
    private UUID roleId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }
}
