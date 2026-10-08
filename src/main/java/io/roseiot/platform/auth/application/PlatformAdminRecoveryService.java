package io.roseiot.platform.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.platform.PlatformPasswordPolicy;
import io.roseiot.platform.auth.domain.InvalidRecoveryTokenException;
import io.roseiot.platform.auth.domain.PasswordValidationException;
import io.roseiot.platform.auth.domain.TokenDigests;
import io.roseiot.platform.auth.infra.ratelimit.PlatformAdminAuthRateLimiter;
import io.roseiot.platform.auth.infra.recovery.RecoveryNotifier;
import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.platform.persistence.PlatformAdminRecoveryRequest;
import io.roseiot.platform.persistence.PlatformAdminRecoveryRequestMapper;
import io.roseiot.platform.support.UuidV7;
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
public class PlatformAdminRecoveryService {

    public static final String ACCEPTED_MESSAGE = "If an account exists, recovery instructions were sent";

    private final PlatformAdminMapper platformAdminMapper;
    private final PlatformAdminRecoveryRequestMapper recoveryRequestMapper;
    private final RecoveryNotifier recoveryNotifier;
    private final PlatformAdminAuthRateLimiter authRateLimiter;
    private final PlatformAdminRefreshSessions refreshSessions;
    private final PasswordEncoder passwordEncoder;

    public PlatformAdminRecoveryService(
            PlatformAdminMapper platformAdminMapper,
            PlatformAdminRecoveryRequestMapper recoveryRequestMapper,
            RecoveryNotifier recoveryNotifier,
            PlatformAdminAuthRateLimiter authRateLimiter,
            PlatformAdminRefreshSessions refreshSessions,
            @Qualifier("platformPasswordEncoder") PasswordEncoder passwordEncoder) {
        this.platformAdminMapper = platformAdminMapper;
        this.recoveryRequestMapper = recoveryRequestMapper;
        this.recoveryNotifier = recoveryNotifier;
        this.authRateLimiter = authRateLimiter;
        this.refreshSessions = refreshSessions;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public String requestRecovery(String rawEmail) {
        String email = PlatformAdminSessionService.normalizeLoginEmail(rawEmail);
        authRateLimiter.onRecoveryRequest(email);
        PlatformAdmin admin = platformAdminMapper.selectOne(
                new LambdaQueryWrapper<PlatformAdmin>().eq(PlatformAdmin::getEmail, email));
        if (admin != null) {
            issueRecoveryToken(admin);
        }
        return ACCEPTED_MESSAGE;
    }

    @Transactional(noRollbackFor = InvalidRecoveryTokenException.class)
    public void confirmRecovery(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRecoveryTokenException();
        }
        PlatformPasswordPolicy.validationError(newPassword)
                .ifPresent(message -> {
                    throw new PasswordValidationException(message);
                });

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

        refreshSessions.revokeAllForAdmin(adminId, now);
    }

    private void issueRecoveryToken(PlatformAdmin admin) {
        String plainToken = TokenDigests.generateUrlSafe256Bit();
        Instant now = Instant.now();

        PlatformAdminRecoveryRequest request = new PlatformAdminRecoveryRequest();
        request.setId(UuidV7.generate());
        request.setPlatformAdminId(admin.getId());
        request.setTokenHash(TokenDigests.sha256Hex(plainToken));
        request.setExpiresAt(now.plus(1, ChronoUnit.HOURS));
        request.setCreatedAt(now);
        recoveryRequestMapper.insert(request);

        recoveryNotifier.sendRecoveryEmail(admin.getEmail(), plainToken);
    }
}
