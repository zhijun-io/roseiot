package io.roseiot.platform.auth.application;

import io.roseiot.platform.auth.domain.InvalidCredentialsException;
import io.roseiot.platform.auth.domain.InvalidRefreshTokenException;
import io.roseiot.platform.auth.domain.TokenDigests;
import io.roseiot.platform.auth.infra.ratelimit.PlatformAdminAuthRateLimiter;
import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.platform.persistence.PlatformAdminRefreshSession;
import io.roseiot.platform.persistence.PlatformAdminRefreshSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAdminSessionServiceTests {

    private static final String PASSWORD = "password1234";

    @Mock
    PlatformAdminMapper platformAdminMapper;

    @Mock
    PlatformAdminRefreshSessionMapper refreshSessionMapper;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    PlatformAdminAuthRateLimiter authRateLimiter;

    @Mock
    PlatformAdminRefreshSessions refreshSessions;

    @InjectMocks
    PlatformAdminSessionService sessionService;

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                    '  Admin@Example.COM ', admin@example.com
                    a@b.com, a@b.com
                    """)
    void normalizeEmail(String raw, String expected) {
        assertThat(PlatformAdminSessionService.normalizeLoginEmail(raw)).isEqualTo(expected);
    }

    @Test
    void loginRejectsUnknownAdminOrBadPassword() {
        when(platformAdminMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> sessionService.login("a@b.com", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);

        PlatformAdmin admin = adminWithHash("hash");
        when(platformAdminMapper.selectOne(any())).thenReturn(admin);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        assertThatThrownBy(() -> sessionService.login("a@b.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginReturnsTokensFromRefreshSessions() {
        UUID adminId = UUID.randomUUID();
        PlatformAdmin admin = adminWithHash("hash");
        admin.setId(adminId);
        when(platformAdminMapper.selectOne(any())).thenReturn(admin);
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);
        when(refreshSessions.issueNewFamily(eq(adminId), any(Instant.class)))
                .thenReturn(new PlatformAdminSessionService.SessionTokens("access-jwt", "refresh-plain"));

        PlatformAdminSessionService.SessionTokens tokens = sessionService.login("a@b.com", PASSWORD);

        assertThat(tokens.accessToken()).isEqualTo("access-jwt");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-plain");
    }

    @Test
    void replayOfRevokedRefreshRevokesFamily() {
        UUID familyId = UUID.randomUUID();
        String rawToken = "replay-token-value";
        String hash = TokenDigests.sha256Hex(rawToken);

        PlatformAdminRefreshSession revoked = session(familyId, hash);
        revoked.setRevokedAt(Instant.now());

        when(refreshSessionMapper.selectOne(any())).thenReturn(null, revoked);

        assertThatThrownBy(() -> sessionService.refresh(rawToken)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshSessions).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    void validRefreshRotatesViaRefreshSessions() {
        UUID familyId = UUID.randomUUID();
        String rawToken = "valid-refresh-token";
        String hash = TokenDigests.sha256Hex(rawToken);

        PlatformAdminRefreshSession active = session(familyId, hash);
        active.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));

        when(refreshSessionMapper.selectOne(any())).thenReturn(active);
        when(refreshSessions.rotate(eq(active), any(Instant.class)))
                .thenReturn(new PlatformAdminSessionService.SessionTokens("access-jwt", "new-refresh"));

        PlatformAdminSessionService.SessionTokens tokens = sessionService.refresh(rawToken);

        assertThat(tokens.accessToken()).isEqualTo("access-jwt");
        assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
    }

    private static PlatformAdmin adminWithHash(String hash) {
        PlatformAdmin admin = new PlatformAdmin();
        admin.setPasswordHash(hash);
        return admin;
    }

    private static PlatformAdminRefreshSession session(UUID familyId, String hash) {
        PlatformAdminRefreshSession session = new PlatformAdminRefreshSession();
        session.setId(UUID.randomUUID());
        session.setFamilyId(familyId);
        session.setTokenHash(hash);
        return session;
    }
}
