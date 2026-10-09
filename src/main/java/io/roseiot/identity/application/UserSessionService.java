package io.roseiot.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.identity.domain.LoginEmails;
import io.roseiot.identity.infra.security.AuthenticationExceptionMapping;
import org.springframework.security.core.AuthenticationException;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRefreshSession;
import io.roseiot.identity.persistence.UserRefreshSessionMapper;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class UserSessionService {

    private final UserMapper userMapper;
    private final UserRefreshSessionMapper refreshSessionMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserRefreshSessions refreshSessions;
    private final TenantMapper tenantMapper;

    public UserSessionService(
            UserMapper userMapper,
            UserRefreshSessionMapper refreshSessionMapper,
            @Qualifier("identityPasswordEncoder") PasswordEncoder passwordEncoder,
            UserRefreshSessions refreshSessions,
            TenantMapper tenantMapper) {
        this.userMapper = userMapper;
        this.refreshSessionMapper = refreshSessionMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshSessions = refreshSessions;
        this.tenantMapper = tenantMapper;
    }

    @Transactional
    public SessionTokens login(String rawEmail, String rawPassword) {
        String email = LoginEmails.normalize(rawEmail);
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null
                || user.getDisabledAt() != null
                || rawPassword == null
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw AuthenticationExceptionMapping.invalidCredentials();
        }
        ensureTenantActiveForUser(user, false);
        return refreshSessions.issueNewFamily(user.getId(), Instant.now());
    }

    @Transactional(noRollbackFor = AuthenticationException.class)
    public SessionTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw AuthenticationExceptionMapping.invalidRefreshToken();
        }

        String tokenHash = TokenDigests.sha256Hex(rawRefreshToken);
        Instant now = Instant.now();

        UserRefreshSession active = refreshSessionMapper.selectOne(
                new LambdaQueryWrapper<UserRefreshSession>()
                        .eq(UserRefreshSession::getTokenHash, tokenHash)
                        .isNull(UserRefreshSession::getRevokedAt)
                        .gt(UserRefreshSession::getExpiresAt, now));

        if (active != null) {
            User user = userMapper.selectById(active.getUserId());
            if (user == null || user.getDisabledAt() != null) {
                throw AuthenticationExceptionMapping.invalidRefreshToken();
            }
            ensureTenantActiveForUser(user, true);
            return refreshSessions.rotate(active, now);
        }

        UserRefreshSession existing = refreshSessionMapper.selectOne(
                new LambdaQueryWrapper<UserRefreshSession>()
                        .eq(UserRefreshSession::getTokenHash, tokenHash));

        if (existing != null && existing.getRevokedAt() != null) {
            refreshSessions.revokeFamily(existing.getFamilyId(), now);
        }

        throw AuthenticationExceptionMapping.invalidRefreshToken();
    }

    @Transactional
    public void logoutCurrentSession(UUID sessionId) {
        refreshSessions.revokeSession(sessionId, Instant.now());
    }

    @Transactional
    public void logoutAllSessions(UUID userId) {
        refreshSessions.revokeAllForUser(userId, Instant.now());
    }

    private void ensureTenantActiveForUser(User user, boolean refreshFlow) {
        if (user.getTenantId() == null) {
            return;
        }
        Tenant tenant = tenantMapper.selectById(user.getTenantId());
        if (tenant == null || tenant.getDisabledAt() != null) {
            if (refreshFlow) {
                throw AuthenticationExceptionMapping.invalidRefreshToken();
            }
            throw AuthenticationExceptionMapping.invalidCredentials();
        }
    }

    public record SessionTokens(String accessToken, String refreshToken) {}
}
