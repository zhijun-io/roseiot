package io.roseiot.iam.user;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("user_credential")
class UserCredentialEntity {

    static UserCredentialEntity from(UserCredential credential) {
        UserCredentialEntity entity = new UserCredentialEntity();
        entity.setUserId(credential.userId());
        entity.setPassword(credential.password());
        entity.setResetToken(credential.resetToken());
        entity.setResetExpiresAt(credential.resetExpiresAt());
        entity.setResetAt(credential.resetAt());
        entity.setCreatedAt(credential.createdAt());
        entity.setUpdatedAt(credential.updatedAt());
        return entity;
    }

    @TableId(type = IdType.INPUT)
    private Long userId;

    private String password;

    private String resetToken;

    private Instant resetExpiresAt;

    private Instant resetAt;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }
    public Instant getResetExpiresAt() { return resetExpiresAt; }
    public void setResetExpiresAt(Instant resetExpiresAt) { this.resetExpiresAt = resetExpiresAt; }
    public Instant getResetAt() { return resetAt; }
    public void setResetAt(Instant resetAt) { this.resetAt = resetAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
