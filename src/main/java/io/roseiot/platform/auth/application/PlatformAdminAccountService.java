package io.roseiot.platform.auth.application;

import io.roseiot.platform.PlatformPasswordPolicy;
import io.roseiot.platform.auth.domain.InvalidCredentialsException;
import io.roseiot.platform.auth.domain.PasswordValidationException;
import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PlatformAdminAccountService {

    private final PlatformAdminMapper platformAdminMapper;
    private final PlatformAdminRefreshSessions refreshSessions;
    private final PasswordEncoder passwordEncoder;

    public PlatformAdminAccountService(
            PlatformAdminMapper platformAdminMapper,
            PlatformAdminRefreshSessions refreshSessions,
            @Qualifier("platformPasswordEncoder") PasswordEncoder passwordEncoder) {
        this.platformAdminMapper = platformAdminMapper;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
    }

    public PlatformAdminProfile getProfile(UUID platformAdminId) {
        PlatformAdmin admin = platformAdminMapper.selectById(platformAdminId);
        if (admin == null) {
            throw new InvalidCredentialsException();
        }
        return new PlatformAdminProfile(admin.getId(), admin.getEmail());
    }

    @Transactional
    public void changePassword(UUID platformAdminId, String currentPassword, String newPassword) {
        PlatformAdmin admin = platformAdminMapper.selectById(platformAdminId);
        if (admin == null
                || currentPassword == null
                || !passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        PlatformPasswordPolicy.validationError(newPassword)
                .ifPresent(message -> {
                    throw new PasswordValidationException(message);
                });

        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        platformAdminMapper.updateById(admin);
        refreshSessions.revokeAllForAdmin(platformAdminId, Instant.now());
    }

    public record PlatformAdminProfile(UUID id, String email) {}
}
