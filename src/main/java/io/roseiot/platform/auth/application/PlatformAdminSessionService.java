package io.roseiot.platform.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.platform.auth.domain.InvalidCredentialsException;
import io.roseiot.platform.auth.domain.InvalidRefreshTokenException;
import io.roseiot.platform.auth.domain.TokenDigests;
import io.roseiot.platform.auth.infra.ratelimit.PlatformAdminAuthRateLimiter;
import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.platform.persistence.PlatformAdminRefreshSession;
import io.roseiot.platform.persistence.PlatformAdminRefreshSessionMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PlatformAdminSessionService {

    private final PlatformAdminMapper platformAdminMapper;
    private final PlatformAdminRefreshSessionMapper refreshSessionMapper;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAdminAuthRateLimiter authRateLimiter;
    private final PlatformAdminRefreshSessions refreshSessions;

    public PlatformAdminSessionService(
            PlatformAdminMapper platformAdminMapper,
            PlatformAdminRefreshSessionMapper refreshSessionMapper,
            @Qualifier("platformPasswordEncoder") PasswordEncoder passwordEncoder,
            PlatformAdminAuthRateLimiter authRateLimiter,
            PlatformAdminRefreshSessions refreshSessions) {
        this.platformAdminMapper = platformAdminMapper;
        this.refreshSessionMapper = refreshSessionMapper;
        this.passwordEncoder = passwordEncoder;
        this.authRateLimiter = authRateLimiter;
        this.refreshSessions = refreshSessions;
    }

    static String normalizeLoginEmail(String rawEmail) {
        if (rawEmail == null) {
            return "";
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public SessionTokens login(String rawEmail, String rawPassword) {
        String email = normalizeLoginEmail(rawEmail);
        authRateLimiter.onLoginAttempt(email);
        PlatformAdmin admin = platformAdminMapper.selectOne(
                new LambdaQueryWrapper<PlatformAdmin>().eq(PlatformAdmin::getEmail, email));
        if (admin == null || rawPassword == null || !passwordEncoder.matches(rawPassword, admin.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return refreshSessions.issueNewFamily(admin.getId(), Instant.now());
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public SessionTokens refresh(String rawRefreshToken) {
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
            return refreshSessions.rotate(active, now);
        }

        PlatformAdminRefreshSession existing = refreshSessionMapper.selectOne(
                new LambdaQueryWrapper<PlatformAdminRefreshSession>()
                        .eq(PlatformAdminRefreshSession::getTokenHash, tokenHash));

        if (existing != null && existing.getRevokedAt() != null) {
            refreshSessions.revokeFamily(existing.getFamilyId(), now);
        }

        throw new InvalidRefreshTokenException();
    }

    @Transactional
    public void logoutCurrentSession(UUID sessionId) {
        refreshSessions.revokeSession(sessionId, Instant.now());
    }

    @Transactional
    public void logoutAllSessions(UUID platformAdminId) {
        refreshSessions.revokeAllForAdmin(platformAdminId, Instant.now());
    }

    public record SessionTokens(String accessToken, String refreshToken) {}
}
