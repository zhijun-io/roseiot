package io.roseiot.platform.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PlatformAdminLogoutUseCase {

    private final PlatformAdminRefreshSessionRevoker sessionRevoker;

    public PlatformAdminLogoutUseCase(PlatformAdminRefreshSessionRevoker sessionRevoker) {
        this.sessionRevoker = sessionRevoker;
    }

    @Transactional
    public void logoutCurrentSession(UUID sessionId) {
        sessionRevoker.revokeSession(sessionId, Instant.now());
    }

    @Transactional
    public void logoutAllSessions(UUID platformAdminId) {
        sessionRevoker.revokeAllForAdmin(platformAdminId, Instant.now());
    }
}
