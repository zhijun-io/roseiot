package io.roseiot.identity.application;

import io.roseiot.identity.domain.PasswordPolicy;
import io.roseiot.identity.domain.InvalidCredentialsException;
import io.roseiot.identity.domain.PasswordValidationException;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class UserAccountService {

    private final UserMapper userMapper;
    private final UserRefreshSessions refreshSessions;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(
            UserMapper userMapper,
            UserRefreshSessions refreshSessions,
            @Qualifier("identityPasswordEncoder") PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
    }

    public UserProfile getProfile(UUID userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new InvalidCredentialsException();
        }
        return new UserProfile(user.getId(), user.getEmail());
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null
                || currentPassword == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        PasswordPolicy.validationError(newPassword)
                .ifPresent(message -> {
                    throw new PasswordValidationException(message);
                });

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setAuthorizationVersion(user.getAuthorizationVersion() + 1);
        userMapper.updateById(user);
        refreshSessions.revokeAllForUser(userId, Instant.now());
    }

    public record UserProfile(UUID id, String email) {}
}
