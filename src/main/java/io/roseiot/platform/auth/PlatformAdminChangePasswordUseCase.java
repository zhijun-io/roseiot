package io.roseiot.platform.auth;

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
public class PlatformAdminChangePasswordUseCase {

    private final PlatformAdminMapper platformAdminMapper;
    private final PlatformAdminRefreshSessionRevoker sessionRevoker;
    private final PasswordEncoder passwordEncoder;

    public PlatformAdminChangePasswordUseCase(
            PlatformAdminMapper platformAdminMapper,
            PlatformAdminRefreshSessionRevoker sessionRevoker,
            @Qualifier("platformPasswordEncoder") PasswordEncoder passwordEncoder) {
        this.platformAdminMapper = platformAdminMapper;
        this.sessionRevoker = sessionRevoker;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(UUID platformAdminId, String currentPassword, String newPassword) {
        PlatformAdmin admin = platformAdminMapper.selectById(platformAdminId);
        if (admin == null
                || currentPassword == null
                || !passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        PlatformPasswordRules.validateNewPassword(newPassword);

        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        platformAdminMapper.updateById(admin);
        sessionRevoker.revokeAllForAdmin(platformAdminId, Instant.now());
    }
}
