package io.roseiot.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.platform.auth.recovery.RecoveryNotificationPort;
import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.platform.persistence.PlatformAdminRecoveryRequest;
import io.roseiot.platform.persistence.PlatformAdminRecoveryRequestMapper;
import io.roseiot.platform.support.UuidV7;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PlatformAdminRecoveryRequestUseCase {

    static final String ACCEPTED_MESSAGE = "If an account exists, recovery instructions were sent";

    private final PlatformAdminMapper platformAdminMapper;
    private final PlatformAdminRecoveryRequestMapper recoveryRequestMapper;
    private final RecoveryNotificationPort recoveryNotificationPort;
    private final PlatformAdminAuthRateLimiter authRateLimiter;

    public PlatformAdminRecoveryRequestUseCase(
            PlatformAdminMapper platformAdminMapper,
            PlatformAdminRecoveryRequestMapper recoveryRequestMapper,
            RecoveryNotificationPort recoveryNotificationPort,
            PlatformAdminAuthRateLimiter authRateLimiter) {
        this.platformAdminMapper = platformAdminMapper;
        this.recoveryRequestMapper = recoveryRequestMapper;
        this.recoveryNotificationPort = recoveryNotificationPort;
        this.authRateLimiter = authRateLimiter;
    }

    @Transactional
    public String requestRecovery(String rawEmail) {
        String email = PlatformAdminLoginUseCase.normalizeEmail(rawEmail);
        authRateLimiter.onRecoveryRequest(email);
        PlatformAdmin admin = platformAdminMapper.selectOne(
                new LambdaQueryWrapper<PlatformAdmin>().eq(PlatformAdmin::getEmail, email));
        if (admin != null) {
            issueRecoveryToken(admin);
        }
        return ACCEPTED_MESSAGE;
    }

    private void issueRecoveryToken(PlatformAdmin admin) {
        String plainToken = OpaqueTokens.generateUrlSafe256Bit();
        Instant now = Instant.now();

        PlatformAdminRecoveryRequest request = new PlatformAdminRecoveryRequest();
        request.setId(UuidV7.generate());
        request.setPlatformAdminId(admin.getId());
        request.setTokenHash(TokenDigests.sha256Hex(plainToken));
        request.setExpiresAt(now.plus(1, ChronoUnit.HOURS));
        request.setCreatedAt(now);
        recoveryRequestMapper.insert(request);

        recoveryNotificationPort.sendRecoveryEmail(admin.getEmail(), plainToken);
    }
}
