package io.roseiot.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.platform.persistence.PlatformAdminRefreshSession;
import io.roseiot.platform.persistence.PlatformAdminRefreshSessionMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PlatformAdminRefreshSessionRevoker {

    private final PlatformAdminRefreshSessionMapper refreshSessionMapper;

    public PlatformAdminRefreshSessionRevoker(PlatformAdminRefreshSessionMapper refreshSessionMapper) {
        this.refreshSessionMapper = refreshSessionMapper;
    }

    void revokeSession(UUID sessionId, Instant revokedAt) {
        if (sessionId == null) {
            return;
        }
        PlatformAdminRefreshSession session = refreshSessionMapper.selectById(sessionId);
        if (session != null && session.getRevokedAt() == null) {
            session.setRevokedAt(revokedAt);
            refreshSessionMapper.updateById(session);
        }
    }

    void revokeAllForAdmin(UUID platformAdminId, Instant revokedAt) {
        List<PlatformAdminRefreshSession> activeSessions = refreshSessionMapper.selectList(
                new LambdaQueryWrapper<PlatformAdminRefreshSession>()
                        .eq(PlatformAdminRefreshSession::getPlatformAdminId, platformAdminId)
                        .isNull(PlatformAdminRefreshSession::getRevokedAt));
        for (PlatformAdminRefreshSession session : activeSessions) {
            session.setRevokedAt(revokedAt);
            refreshSessionMapper.updateById(session);
        }
    }
}
