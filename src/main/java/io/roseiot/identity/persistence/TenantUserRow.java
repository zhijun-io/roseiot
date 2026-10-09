package io.roseiot.identity.persistence;

import java.time.Instant;
import java.util.UUID;

/** Row shape for tenant-scoped user listing queries. */
public class TenantUserRow {

    private UUID id;
    private String email;
    private Instant disabledAt;
    private String roleCode;

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

    public Instant getDisabledAt() {
        return disabledAt;
    }

    public void setDisabledAt(Instant disabledAt) {
        this.disabledAt = disabledAt;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }
}
