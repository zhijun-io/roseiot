package io.roseiot.identity.application;

import org.springframework.security.authentication.BadCredentialsException;
import io.roseiot.identity.infra.security.InvalidRefreshTokenAuthenticationException;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRefreshSession;
import io.roseiot.identity.persistence.UserRefreshSessionMapper;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserSessionServiceTest {

    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000005");
    private static final UserSessionService.SessionTokens TOKENS =
            new UserSessionService.SessionTokens("access-token", "refresh-token");

    private UserMapper userMapper;
    private UserRefreshSessionMapper refreshSessionMapper;
    private PasswordEncoder passwordEncoder;
    private UserRefreshSessions refreshSessions;
    private TenantMapper tenantMapper;
    private UserSessionService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        refreshSessionMapper = mock(UserRefreshSessionMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        refreshSessions = mock(UserRefreshSessions.class);
        tenantMapper = mock(TenantMapper.class);
        cut = new UserSessionService(userMapper, refreshSessionMapper, passwordEncoder, refreshSessions, tenantMapper);
    }

    private User activeUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("ops@roseiot.io");
        user.setPasswordHash("stored-hash");
        user.setAuthorizationVersion(2);
        return user;
    }

    @Test
    void shouldIssueNewFamilyOnSuccessfulLogin() {
        when(userMapper.selectOne(any())).thenReturn(activeUser());
        when(passwordEncoder.matches("correct horse 42", "stored-hash")).thenReturn(true);
        when(refreshSessions.issueNewFamily(eq(USER_ID), any(Instant.class))).thenReturn(TOKENS);

        assertThat(cut.login("ops@roseiot.io", "correct horse 42")).isSameAs(TOKENS);
    }

    @Test
    void shouldRejectUnknownEmailWithoutComparingPasswords() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> cut.login("ghost@roseiot.io", "correct horse 42"))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(passwordEncoder, refreshSessions);
    }

    @Test
    void shouldRejectDisabledUserEvenWithTheRightPassword() {
        User disabled = activeUser();
        disabled.setDisabledAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(userMapper.selectOne(any())).thenReturn(disabled);

        assertThatThrownBy(() -> cut.login("ops@roseiot.io", "correct horse 42"))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(refreshSessions);
    }

    @Test
    void shouldRejectMissingPasswordWithoutConsultingTheEncoder() {
        when(userMapper.selectOne(any())).thenReturn(activeUser());

        assertThatThrownBy(() -> cut.login("ops@roseiot.io", null))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectWrongPasswordWithoutIssuingSessions() {
        when(userMapper.selectOne(any())).thenReturn(activeUser());
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> cut.login("ops@roseiot.io", "wrong password 1"))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(refreshSessions);
    }

    @Test
    void shouldRejectRefreshTokenThatIsMissingOrBlank() {
        assertThatThrownBy(() -> cut.refresh(null)).isInstanceOf(InvalidRefreshTokenAuthenticationException.class);
        assertThatThrownBy(() -> cut.refresh("  ")).isInstanceOf(InvalidRefreshTokenAuthenticationException.class);
        verifyNoInteractions(refreshSessionMapper, refreshSessions);
    }

    @Test
    void shouldRotateTheSessionWhenRefreshTokenIsStillActive() {
        UserRefreshSession active = new UserRefreshSession();
        active.setId(UUID.randomUUID());
        active.setUserId(USER_ID);
        when(refreshSessionMapper.selectOne(any())).thenReturn(active);
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser());
        when(refreshSessions.rotate(eq(active), any(Instant.class))).thenReturn(TOKENS);

        assertThat(cut.refresh("presented-refresh-token")).isSameAs(TOKENS);
        verify(refreshSessionMapper).selectOne(any());
    }

    @Test
    void shouldRejectLoginWhenTenantIsDisabled() {
        UUID tenantId = UUID.randomUUID();
        User tenantUser = activeUser();
        tenantUser.setTenantId(tenantId);
        when(userMapper.selectOne(any())).thenReturn(tenantUser);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        Tenant disabled = new Tenant();
        disabled.setId(tenantId);
        disabled.setDisabledAt(Instant.now());
        when(tenantMapper.selectById(tenantId)).thenReturn(disabled);

        assertThatThrownBy(() -> cut.login("ops@roseiot.io", "correct horse 42"))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(refreshSessions);
    }

    @Test
    void shouldRevokeTheWholeFamilyWhenARefreshTokenIsReused() {
        UUID familyId = UUID.randomUUID();
        UserRefreshSession alreadyRotated = new UserRefreshSession();
        alreadyRotated.setId(UUID.randomUUID());
        alreadyRotated.setFamilyId(familyId);
        alreadyRotated.setRevokedAt(Instant.now().minusSeconds(60));
        when(refreshSessionMapper.selectOne(any())).thenReturn(null, alreadyRotated);

        assertThatThrownBy(() -> cut.refresh("stolen-refresh-token"))
                .isInstanceOf(InvalidRefreshTokenAuthenticationException.class);
        verify(refreshSessions).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    void shouldNotRevokeAnyFamilyForARefreshTokenThatWasNeverIssued() {
        when(refreshSessionMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> cut.refresh("invented-refresh-token"))
                .isInstanceOf(InvalidRefreshTokenAuthenticationException.class);
        verify(refreshSessions, never()).revokeFamily(any(UUID.class), any(Instant.class));
    }

    @Test
    void shouldNotRevokeFamilyWhenTheFoundSessionIsNotRevoked() {
        UserRefreshSession expiredButNotRevoked = new UserRefreshSession();
        expiredButNotRevoked.setId(UUID.randomUUID());
        expiredButNotRevoked.setFamilyId(UUID.randomUUID());
        when(refreshSessionMapper.selectOne(any())).thenReturn(null, expiredButNotRevoked);

        assertThatThrownBy(() -> cut.refresh("expired-refresh-token"))
                .isInstanceOf(InvalidRefreshTokenAuthenticationException.class);
        verify(refreshSessions, never()).revokeFamily(any(UUID.class), any(Instant.class));
    }

    @Test
    void shouldRevokeOnlyTheCurrentSessionOnLogout() {
        UUID sessionId = UUID.randomUUID();

        cut.logoutCurrentSession(sessionId);

        verify(refreshSessions).revokeSession(eq(sessionId), any(Instant.class));
        verify(refreshSessions, never()).revokeAllForUser(any(UUID.class), any(Instant.class));
    }

    @Test
    void shouldRevokeEverySessionOnLogoutAll() {
        cut.logoutAllSessions(USER_ID);

        verify(refreshSessions).revokeAllForUser(eq(USER_ID), any(Instant.class));
        verify(refreshSessions, never()).revokeSession(any(UUID.class), any(Instant.class));
    }
}
