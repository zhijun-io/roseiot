package io.roseiot.auth;

import java.time.Instant;

interface SessionLifecycle {
    SessionTokens createSession(long userId, boolean passwordChangeRequired);

    String issueAccessToken(long sessionId, long userId, boolean passwordChangeRequired);

    void revokeAllForUser(long userId);

    boolean isActive(long sessionId, long userId, Instant now);

    record SessionTokens(
            long sessionId,
            String accessToken,
            String refreshToken,
            long expiresIn,
            long refreshExpiresIn,
            boolean passwordChangeRequired) {}
}
