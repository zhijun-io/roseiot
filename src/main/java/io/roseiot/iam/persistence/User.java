package io.roseiot.iam.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.roseiot.iam.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

@TableName("users")
public class User {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID tenantId;

    private String email;

    private int authorizationVersion;

    private UserStatus status;

    private Instant createdAt;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getAuthorizationVersion() {
        return authorizationVersion;
    }

    public void setAuthorizationVersion(int authorizationVersion) {
        this.authorizationVersion = authorizationVersion;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

}
