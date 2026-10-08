package io.roseiot.platform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.platform.persistence.PlatformAdminRefreshSession;
import io.roseiot.platform.persistence.PlatformAdminRefreshSessionMapper;
import io.roseiot.platform.support.UuidV7;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PlatformAdminLoginUseCase {

    private final PlatformAdminMapper platformAdminMapper;
    private final PlatformAdminRefreshSessionMapper refreshSessionMapper;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuthTokenIssuer tokenIssuer;
    private final PlatformAdminAuthRateLimiter authRateLimiter;

    public PlatformAdminLoginUseCase(
            PlatformAdminMapper platformAdminMapper,
            PlatformAdminRefreshSessionMapper refreshSessionMapper,
            @Qualifier("platformPasswordEncoder") PasswordEncoder passwordEncoder,
            PlatformAuthTokenIssuer tokenIssuer,
            PlatformAdminAuthRateLimiter authRateLimiter) {
        this.platformAdminMapper = platformAdminMapper;
        this.refreshSessionMapper = refreshSessionMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.authRateLimiter = authRateLimiter;
    }

    @Transactional
    public LoginTokens login(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);
        authRateLimiter.onLoginAttempt(email);
        PlatformAdmin admin = platformAdminMapper.selectOne(
                new LambdaQueryWrapper<PlatformAdmin>().eq(PlatformAdmin::getEmail, email));
        if (admin == null || rawPassword == null || !passwordEncoder.matches(rawPassword, admin.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        UUID sessionId = UuidV7.generate();
        UUID familyId = UuidV7.generate();
        String refreshToken = OpaqueTokens.generateUrlSafe256Bit();
        Instant now = Instant.now();

        PlatformAdminRefreshSession session = new PlatformAdminRefreshSession();
        session.setId(sessionId);
        session.setPlatformAdminId(admin.getId());
        session.setFamilyId(familyId);
        session.setTokenHash(TokenDigests.sha256Hex(refreshToken));
        session.setExpiresAt(now.plus(7, ChronoUnit.DAYS));
        session.setCreatedAt(now);
        refreshSessionMapper.insert(session);

        String accessToken = tokenIssuer.issueAccessToken(admin.getId(), sessionId);
        return new LoginTokens(accessToken, refreshToken);
    }

    static String normalizeEmail(String rawEmail) {
        if (rawEmail == null) {
            return "";
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    public record LoginTokens(String accessToken, String refreshToken) {}
}
