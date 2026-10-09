package io.roseiot.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.identity.domain.LoginEmails;
import io.roseiot.identity.domain.PasswordPolicy;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.identity.infra.passwordreset.PasswordResetNotifier;
import io.roseiot.identity.persistence.PasswordResetRequest;
import io.roseiot.identity.persistence.PasswordResetRequestMapper;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.shared.util.UuidV7;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PasswordResetService {

    private final UserMapper userMapper;
    private final PasswordResetRequestMapper passwordResetRequestMapper;
    private final PasswordResetNotifier passwordResetNotifier;
    private final UserRefreshSessions refreshSessions;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(
            UserMapper userMapper,
            PasswordResetRequestMapper passwordResetRequestMapper,
            PasswordResetNotifier passwordResetNotifier,
            UserRefreshSessions refreshSessions,
            @Qualifier("identityPasswordEncoder") PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordResetRequestMapper = passwordResetRequestMapper;
        this.passwordResetNotifier = passwordResetNotifier;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void requestResetByEmail(String rawEmail) {
        String email = LoginEmails.normalize(rawEmail);
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user != null) {
            issueResetToken(user);
        }
    }

    public ResetTokenState checkResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return ResetTokenState.INVALID;
        }
        String tokenHash = TokenDigests.sha256Hex(rawToken);
        PasswordResetRequest row = passwordResetRequestMapper.selectOne(
                new LambdaQueryWrapper<PasswordResetRequest>()
                        .eq(PasswordResetRequest::getTokenHash, tokenHash));
        if (row == null || row.getUsedAt() != null) {
            return ResetTokenState.INVALID;
        }
        if (!row.getExpiresAt().isAfter(Instant.now())) {
            return ResetTokenState.EXPIRED;
        }
        return ResetTokenState.VALID;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void confirmPasswordReset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ApiErrorCode.INVALID_RESET_TOKEN);
        }
        PasswordPolicy.ensureValid(newPassword, ApiErrorCode.VALIDATION_ERROR);

        String tokenHash = TokenDigests.sha256Hex(rawToken);
        Instant now = Instant.now();

        PasswordResetRequest active = passwordResetRequestMapper.selectOne(
                new LambdaQueryWrapper<PasswordResetRequest>()
                        .eq(PasswordResetRequest::getTokenHash, tokenHash)
                        .isNull(PasswordResetRequest::getUsedAt)
                        .gt(PasswordResetRequest::getExpiresAt, now));

        if (active == null) {
            throw new BusinessException(ApiErrorCode.INVALID_RESET_TOKEN);
        }

        UUID userId = active.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ApiErrorCode.INVALID_RESET_TOKEN);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setAuthorizationVersion(user.getAuthorizationVersion() + 1);
        userMapper.updateById(user);

        active.setUsedAt(now);
        passwordResetRequestMapper.updateById(active);

        refreshSessions.revokeAllForUser(userId, now);
    }

    private void issueResetToken(User user) {
        String plainToken = TokenDigests.generateUrlSafe256Bit();
        Instant now = Instant.now();

        PasswordResetRequest request = new PasswordResetRequest();
        request.setId(UuidV7.generate());
        request.setUserId(user.getId());
        request.setTokenHash(TokenDigests.sha256Hex(plainToken));
        request.setExpiresAt(now.plus(1, ChronoUnit.HOURS));
        request.setCreatedAt(now);
        passwordResetRequestMapper.insert(request);

        passwordResetNotifier.sendPasswordResetEmail(user.getEmail(), plainToken);
    }

    public enum ResetTokenState {
        VALID,
        EXPIRED,
        INVALID
    }
}
