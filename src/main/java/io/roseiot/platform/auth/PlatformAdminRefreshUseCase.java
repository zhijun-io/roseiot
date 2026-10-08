package io.roseiot.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.platform.persistence.PlatformAdminRefreshSession;
import io.roseiot.platform.persistence.PlatformAdminRefreshSessionMapper;
import io.roseiot.platform.support.UuidV7;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PlatformAdminRefreshUseCase {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PlatformAdminRefreshSessionMapper refreshSessionMapper;
    private final PlatformAuthTokenIssuer tokenIssuer;

    public PlatformAdminRefreshUseCase(
            PlatformAdminRefreshSessionMapper refreshSessionMapper, PlatformAuthTokenIssuer tokenIssuer) {
        this.refreshSessionMapper = refreshSessionMapper;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public PlatformAdminLoginUseCase.LoginTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        String tokenHash = TokenDigests.sha256Hex(rawRefreshToken);
        Instant now = Instant.now();

        PlatformAdminRefreshSession active = refreshSessionMapper.selectOne(
                new LambdaQueryWrapper<PlatformAdminRefreshSession>()
                        .eq(PlatformAdminRefreshSession::getTokenHash, tokenHash)
                        .isNull(PlatformAdminRefreshSession::getRevokedAt)
                        .gt(PlatformAdminRefreshSession::getExpiresAt, now));

        if (active != null) {
            return rotate(active, now);
        }

        PlatformAdminRefreshSession existing = refreshSessionMapper.selectOne(
                new LambdaQueryWrapper<PlatformAdminRefreshSession>()
                        .eq(PlatformAdminRefreshSession::getTokenHash, tokenHash));

        if (existing != null && existing.getRevokedAt() != null) {
            revokeFamily(existing.getFamilyId(), now);
        }

        throw new InvalidRefreshTokenException();
    }

    private PlatformAdminLoginUseCase.LoginTokens rotate(PlatformAdminRefreshSession current, Instant now) {
        current.setRevokedAt(now);
        refreshSessionMapper.updateById(current);

        UUID newSessionId = UuidV7.generate();
        String refreshToken = generateOpaqueToken();

        PlatformAdminRefreshSession next = new PlatformAdminRefreshSession();
        next.setId(newSessionId);
        next.setPlatformAdminId(current.getPlatformAdminId());
        next.setFamilyId(current.getFamilyId());
        next.setTokenHash(TokenDigests.sha256Hex(refreshToken));
        next.setExpiresAt(now.plus(7, ChronoUnit.DAYS));
        next.setCreatedAt(now);
        refreshSessionMapper.insert(next);

        String accessToken = tokenIssuer.issueAccessToken(current.getPlatformAdminId(), newSessionId);
        return new PlatformAdminLoginUseCase.LoginTokens(accessToken, refreshToken);
    }

    private void revokeFamily(UUID familyId, Instant revokedAt) {
        List<PlatformAdminRefreshSession> activeSessions = refreshSessionMapper.selectList(
                new LambdaQueryWrapper<PlatformAdminRefreshSession>()
                        .eq(PlatformAdminRefreshSession::getFamilyId, familyId)
                        .isNull(PlatformAdminRefreshSession::getRevokedAt));
        for (PlatformAdminRefreshSession session : activeSessions) {
            session.setRevokedAt(revokedAt);
            refreshSessionMapper.updateById(session);
        }
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
