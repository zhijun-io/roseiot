package io.roseiot.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.identity.domain.InvalidCredentialsException;
import io.roseiot.identity.domain.LoginEmails;
import io.roseiot.identity.domain.InvalidRefreshTokenException;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRefreshSession;
import io.roseiot.identity.persistence.UserRefreshSessionMapper;
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

    public UserSessionService(
            UserMapper userMapper,
            UserRefreshSessionMapper refreshSessionMapper,
            @Qualifier("identityPasswordEncoder") PasswordEncoder passwordEncoder,
            UserRefreshSessions refreshSessions) {
        this.userMapper = userMapper;
        this.refreshSessionMapper = refreshSessionMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshSessions = refreshSessions;
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
            throw new InvalidCredentialsException();
        }
        return refreshSessions.issueNewFamily(user.getId(), Instant.now());
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public SessionTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        String tokenHash = TokenDigests.sha256Hex(rawRefreshToken);
        Instant now = Instant.now();

        UserRefreshSession active = refreshSessionMapper.selectOne(
                new LambdaQueryWrapper<UserRefreshSession>()
                        .eq(UserRefreshSession::getTokenHash, tokenHash)
                        .isNull(UserRefreshSession::getRevokedAt)
                        .gt(UserRefreshSession::getExpiresAt, now));

        if (active != null) {
            return refreshSessions.rotate(active, now);
        }

        UserRefreshSession existing = refreshSessionMapper.selectOne(
                new LambdaQueryWrapper<UserRefreshSession>()
                        .eq(UserRefreshSession::getTokenHash, tokenHash));

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
    public void logoutAllSessions(UUID userId) {
        refreshSessions.revokeAllForUser(userId, Instant.now());
    }

    public record SessionTokens(String accessToken, String refreshToken) {}
}
