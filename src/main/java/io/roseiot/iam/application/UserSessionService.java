package io.roseiot.iam.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.iam.domain.TokenDigests;
import io.roseiot.iam.domain.UserStatus;
import io.roseiot.iam.infra.security.AuthenticationExceptionMapping;
import io.roseiot.iam.infra.security.JwtTokenFactory;
import io.roseiot.iam.infra.security.SecurityUser;
import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserMapper;
import io.roseiot.iam.persistence.UserSession;
import io.roseiot.iam.persistence.UserSessionMapper;
import io.roseiot.shared.util.UuidV7;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class UserSessionService {

    private final UserMapper userMapper;
    private final UserSessionMapper sessionMapper;
    private final TenantMapper tenantMapper;
    private final JwtTokenFactory jwtTokenFactory;

    public UserSessionService(
            UserMapper userMapper,
            UserSessionMapper sessionMapper,
            TenantMapper tenantMapper,
            JwtTokenFactory jwtTokenFactory) {
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
        this.tenantMapper = tenantMapper;
        this.jwtTokenFactory = jwtTokenFactory;
    }

    @Transactional
    public SessionTokens openSession(SecurityUser principal) {
        return openSessionForUser(principal.userId());
    }

    @Transactional
    public SessionTokens openSessionForUser(UUID userId) {
        User user = requireUser(userId);
        if (user.getStatus() != UserStatus.ACTIVE) throw AuthenticationExceptionMapping.invalidCredentials();
        ensureTenantActive(user.getTenantId(), false);
        return issueNewFamily(user, Instant.now());
    }

    @Transactional(noRollbackFor = AuthenticationException.class)
    public SessionTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw AuthenticationExceptionMapping.invalidRefreshToken();
        }

        JwtTokenFactory.RefreshClaims claims = jwtTokenFactory.decodeRefreshToken(rawRefreshToken);
        String tokenHash = TokenDigests.sha256Hex(rawRefreshToken);
        Instant now = Instant.now();

        UserSession active = sessionMapper.selectOne(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getTokenHash, tokenHash)
                .isNull(UserSession::getRevokedAt)
                .gt(UserSession::getExpiresAt, now));

        if (active != null) {
            if (!matches(active, claims)) throw AuthenticationExceptionMapping.invalidRefreshToken();
            User user = userMapper.selectById(active.getUserId());
            if (user == null || user.getStatus() != UserStatus.ACTIVE) {
                throw AuthenticationExceptionMapping.invalidRefreshToken();
            }
            ensureTenantActive(user.getTenantId(), true);
            SessionTokens rotated = rotate(active, user, now);
            if (rotated != null) return rotated;
            revokeFamily(active.getFamilyId(), now);
            throw AuthenticationExceptionMapping.invalidRefreshToken();
        }

        UserSession existing = sessionMapper.selectOne(
                new LambdaQueryWrapper<UserSession>().eq(UserSession::getTokenHash, tokenHash));
        if (existing != null && existing.getRevokedAt() != null && matches(existing, claims)) {
            revokeFamily(existing.getFamilyId(), now);
        }
        throw AuthenticationExceptionMapping.invalidRefreshToken();
    }

    @Transactional
    public void logoutCurrentSession(UUID sessionId) {
        revokeSession(sessionId, Instant.now());
    }

    @Transactional
    public void logoutAllSessions(UUID userId) {
        revokeSessions(sessionMapper.selectList(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .isNull(UserSession::getRevokedAt)), Instant.now());
    }

    private SessionTokens issueNewFamily(User user, Instant now) {
        UUID sessionId = UuidV7.generate();
        UUID familyId = UuidV7.generate();
        String refreshToken = jwtTokenFactory.createRefreshToken(user.getId(), sessionId, familyId, now);
        insertSession(sessionId, user.getId(), familyId, refreshToken, now);
        return new SessionTokens(jwtTokenFactory.createAccessToken(user, sessionId, now), refreshToken);
    }

    private SessionTokens rotate(UserSession current, User user, Instant now) {
        if (sessionMapper.revokeForRotation(current.getId(), now) != 1) return null;
        UUID nextSessionId = UuidV7.generate();
        String refreshToken = jwtTokenFactory.createRefreshToken(
                current.getUserId(), nextSessionId, current.getFamilyId(), now);
        insertSession(nextSessionId, current.getUserId(), current.getFamilyId(), refreshToken, now);
        return new SessionTokens(jwtTokenFactory.createAccessToken(user, nextSessionId, now), refreshToken);
    }

    private void revokeSession(UUID sessionId, Instant revokedAt) {
        if (sessionId == null) return;
        UserSession session = sessionMapper.selectById(sessionId);
        if (session != null && session.getRevokedAt() == null) {
            session.setRevokedAt(revokedAt);
            sessionMapper.updateById(session);
        }
    }

    private void revokeFamily(UUID familyId, Instant revokedAt) {
        revokeSessions(sessionMapper.selectList(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getFamilyId, familyId)
                .isNull(UserSession::getRevokedAt)), revokedAt);
    }

    private void revokeSessions(List<UserSession> activeSessions, Instant revokedAt) {
        for (UserSession session : activeSessions) {
            session.setRevokedAt(revokedAt);
            sessionMapper.updateById(session);
        }
    }

    private void insertSession(UUID sessionId, UUID userId, UUID familyId, String refreshToken, Instant now) {
        UserSession session = new UserSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setFamilyId(familyId);
        session.setTokenHash(TokenDigests.sha256Hex(refreshToken));
        session.setExpiresAt(now.plus(7, ChronoUnit.DAYS));
        session.setCreatedAt(now);
        sessionMapper.insert(session);
    }

    private User requireUser(UUID userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new IllegalStateException("Cannot issue session tokens for missing user");
        return user;
    }

    private static boolean matches(UserSession session, JwtTokenFactory.RefreshClaims claims) {
        return session.getId().equals(claims.sessionId())
                && session.getUserId().equals(claims.userId())
                && session.getFamilyId().equals(claims.familyId());
    }

    private void ensureTenantActive(UUID tenantId, boolean refreshFlow) {
        if (tenantId == null) throw AuthenticationExceptionMapping.invalidCredentials();
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getDisabledAt() != null) {
            if (refreshFlow) throw AuthenticationExceptionMapping.invalidRefreshToken();
            throw AuthenticationExceptionMapping.invalidCredentials();
        }
    }

    public record SessionTokens(String accessToken, String refreshToken) {}
}
