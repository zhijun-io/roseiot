package io.roseiot.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.platform.persistence.PlatformAdminRecoveryRequest;
import io.roseiot.platform.persistence.PlatformAdminRecoveryRequestMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PlatformAdminRecoveryConfirmUseCase {

    private final PlatformAdminMapper platformAdminMapper;
    private final PlatformAdminRecoveryRequestMapper recoveryRequestMapper;
    private final PlatformAdminRefreshSessionRevoker sessionRevoker;
    private final PasswordEncoder passwordEncoder;

    public PlatformAdminRecoveryConfirmUseCase(
            PlatformAdminMapper platformAdminMapper,
            PlatformAdminRecoveryRequestMapper recoveryRequestMapper,
            PlatformAdminRefreshSessionRevoker sessionRevoker,
            @Qualifier("platformPasswordEncoder") PasswordEncoder passwordEncoder) {
        this.platformAdminMapper = platformAdminMapper;
        this.recoveryRequestMapper = recoveryRequestMapper;
        this.sessionRevoker = sessionRevoker;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(noRollbackFor = InvalidRecoveryTokenException.class)
    public void confirmRecovery(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRecoveryTokenException();
        }
        PlatformPasswordRules.validateNewPassword(newPassword);

        String tokenHash = TokenDigests.sha256Hex(rawToken);
        Instant now = Instant.now();

        PlatformAdminRecoveryRequest active = recoveryRequestMapper.selectOne(
                new LambdaQueryWrapper<PlatformAdminRecoveryRequest>()
                        .eq(PlatformAdminRecoveryRequest::getTokenHash, tokenHash)
                        .isNull(PlatformAdminRecoveryRequest::getUsedAt)
                        .gt(PlatformAdminRecoveryRequest::getExpiresAt, now));

        if (active == null) {
            throw new InvalidRecoveryTokenException();
        }

        UUID adminId = active.getPlatformAdminId();
        PlatformAdmin admin = platformAdminMapper.selectById(adminId);
        if (admin == null) {
            throw new InvalidRecoveryTokenException();
        }

        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        platformAdminMapper.updateById(admin);

        active.setUsedAt(now);
        recoveryRequestMapper.updateById(active);

        sessionRevoker.revokeAllForAdmin(adminId, now);
    }
}
