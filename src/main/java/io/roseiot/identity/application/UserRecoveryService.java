package io.roseiot.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.identity.domain.PasswordPolicy;
import io.roseiot.identity.domain.InvalidRecoveryTokenException;
import io.roseiot.identity.domain.LoginEmails;
import io.roseiot.identity.domain.PasswordValidationException;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.identity.infra.recovery.UserRecoveryNotifier;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRecoveryRequest;
import io.roseiot.identity.persistence.UserRecoveryRequestMapper;
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
public class UserRecoveryService {

    public static final String ACCEPTED_MESSAGE = "If an account exists, recovery instructions were sent";

    private final UserMapper userMapper;
    private final UserRecoveryRequestMapper recoveryRequestMapper;
    private final UserRecoveryNotifier userRecoveryNotifier;
    private final UserRefreshSessions refreshSessions;
    private final PasswordEncoder passwordEncoder;

    public UserRecoveryService(
            UserMapper userMapper,
            UserRecoveryRequestMapper recoveryRequestMapper,
            UserRecoveryNotifier userRecoveryNotifier,
            UserRefreshSessions refreshSessions,
            @Qualifier("identityPasswordEncoder") PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.recoveryRequestMapper = recoveryRequestMapper;
        this.userRecoveryNotifier = userRecoveryNotifier;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public String requestRecovery(String rawEmail) {
        String email = LoginEmails.normalize(rawEmail);
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user != null) {
            issueRecoveryToken(user);
        }
        return ACCEPTED_MESSAGE;
    }

    @Transactional(noRollbackFor = InvalidRecoveryTokenException.class)
    public void confirmRecovery(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRecoveryTokenException();
        }
        PasswordPolicy.validationError(newPassword)
                .ifPresent(message -> {
                    throw new PasswordValidationException(message);
                });

        String tokenHash = TokenDigests.sha256Hex(rawToken);
        Instant now = Instant.now();

        UserRecoveryRequest active = recoveryRequestMapper.selectOne(
                new LambdaQueryWrapper<UserRecoveryRequest>()
                        .eq(UserRecoveryRequest::getTokenHash, tokenHash)
                        .isNull(UserRecoveryRequest::getUsedAt)
                        .gt(UserRecoveryRequest::getExpiresAt, now));

        if (active == null) {
            throw new InvalidRecoveryTokenException();
        }

        UUID userId = active.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new InvalidRecoveryTokenException();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setAuthorizationVersion(user.getAuthorizationVersion() + 1);
        userMapper.updateById(user);

        active.setUsedAt(now);
        recoveryRequestMapper.updateById(active);

        refreshSessions.revokeAllForUser(userId, now);
    }

    private void issueRecoveryToken(User user) {
        String plainToken = TokenDigests.generateUrlSafe256Bit();
        Instant now = Instant.now();

        UserRecoveryRequest request = new UserRecoveryRequest();
        request.setId(UuidV7.generate());
        request.setUserId(user.getId());
        request.setTokenHash(TokenDigests.sha256Hex(plainToken));
        request.setExpiresAt(now.plus(1, ChronoUnit.HOURS));
        request.setCreatedAt(now);
        recoveryRequestMapper.insert(request);

        userRecoveryNotifier.sendRecoveryEmail(user.getEmail(), plainToken);
    }
}
