package io.roseiot.identity.application;

import io.roseiot.identity.domain.InvalidCredentialsException;
import io.roseiot.identity.domain.InvalidRefreshTokenException;
import io.roseiot.identity.domain.LoginEmails;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRefreshSession;
import io.roseiot.identity.persistence.UserRefreshSessionMapper;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceTests {

    private static final String PASSWORD = "password1234";

    @Mock
    UserMapper userMapper;

    @Mock
    UserRefreshSessionMapper refreshSessionMapper;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    UserRefreshSessions refreshSessions;

    @InjectMocks
    UserSessionService sessionService;

    @ParameterizedTest
    @CsvSource(
            textBlock =
                    """
                    '  Admin@Example.COM ', admin@example.com
                    a@b.com, a@b.com
                    """)
    void normalizeLoginEmail(String raw, String expected) {
        assertThat(LoginEmails.normalize(raw)).isEqualTo(expected);
    }

    @Test
    void loginRejectsUnknownUserOrBadPassword() {
        when(userMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> sessionService.login("a@b.com", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);

        User user = userWithHash("hash");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(false);
        assertThatThrownBy(() -> sessionService.login("a@b.com", PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginReturnsTokensWhenCredentialsValid() {
        UUID userId = UUID.randomUUID();
        User user = userWithHash("hash");
        user.setId(userId);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches(PASSWORD, "hash")).thenReturn(true);
        when(refreshSessions.issueNewFamily(eq(userId), any(Instant.class)))
                .thenReturn(new UserSessionService.SessionTokens("access-jwt", "refresh-plain"));

        UserSessionService.SessionTokens tokens = sessionService.login("a@b.com", PASSWORD);

        assertThat(tokens.accessToken()).isEqualTo("access-jwt");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-plain");
    }

    @Test
    void refreshRejectsBlankToken() {
        assertThatThrownBy(() -> sessionService.refresh("  "))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refreshRotatesWhenActiveSessionFound() {
        String rawToken = "refresh-plain";
        String hash = TokenDigests.sha256Hex(rawToken);
        UUID familyId = UUID.randomUUID();
        UserRefreshSession active = session(familyId, hash);
        active.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));

        when(refreshSessionMapper.selectOne(any())).thenReturn(active);
        when(refreshSessions.rotate(eq(active), any(Instant.class)))
                .thenReturn(new UserSessionService.SessionTokens("access-jwt", "new-refresh"));

        UserSessionService.SessionTokens tokens = sessionService.refresh(rawToken);

        assertThat(tokens.accessToken()).isEqualTo("access-jwt");
        assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
    }

    private static User userWithHash(String hash) {
        User user = new User();
        user.setPasswordHash(hash);
        return user;
    }

    private static UserRefreshSession session(UUID familyId, String hash) {
        UserRefreshSession session = new UserRefreshSession();
        session.setId(UUID.randomUUID());
        session.setFamilyId(familyId);
        session.setTokenHash(hash);
        return session;
    }
}
