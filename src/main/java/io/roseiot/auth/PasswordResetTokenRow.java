package io.roseiot.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("password_reset_tokens")
class PasswordResetTokenRow {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String tokenDigest;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant usedAt;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    Long getUserId() {
        return userId;
    }

    void setUserId(Long userId) {
        this.userId = userId;
    }

    String getTokenDigest() {
        return tokenDigest;
    }

    void setTokenDigest(String tokenDigest) {
        this.tokenDigest = tokenDigest;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    Instant getUsedAt() {
        return usedAt;
    }

    void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }
}
