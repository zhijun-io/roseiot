package io.roseiot.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("user_session_refresh_tokens")
class SessionRefreshTokenRow {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private String tokenDigest;
    private Instant issuedAt;
    private Instant expiresAt;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    Long getSessionId() {
        return sessionId;
    }

    void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    String getTokenDigest() {
        return tokenDigest;
    }

    void setTokenDigest(String tokenDigest) {
        this.tokenDigest = tokenDigest;
    }

    Instant getIssuedAt() {
        return issuedAt;
    }

    void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
