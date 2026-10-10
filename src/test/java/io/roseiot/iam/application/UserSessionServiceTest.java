package io.roseiot.iam.application;

import io.roseiot.iam.domain.ActionCode;
import io.roseiot.iam.domain.TokenDigests;
import io.roseiot.iam.domain.UserStatus;
import io.roseiot.iam.infra.security.AuthenticationExceptionMapping;
import io.roseiot.iam.infra.security.JwtTokenFactory;
import io.roseiot.iam.infra.security.SecurityUser;
import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserMapper;
import io.roseiot.iam.persistence.UserSession;
import io.roseiot.iam.persistence.UserSessionMapper;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserSessionServiceTest {

    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000005");
    private static final UUID TENANT_ID = UUID.fromString("019a0000-0000-7000-8000-0000000000f1");
    private static final UserSessionService.SessionTokens TOKENS =
            new UserSessionService.SessionTokens("access-token", "refresh-token");

    private UserMapper userMapper;
    private UserSessionMapper sessionMapper;
    private JwtTokenFactory jwtTokenFactory;
    private TenantMapper tenantMapper;
    private UserSessionService cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        sessionMapper = mock(UserSessionMapper.class);
        jwtTokenFactory = mock(JwtTokenFactory.class);
        tenantMapper = mock(TenantMapper.class);
        cut = new UserSessionService(userMapper, sessionMapper, tenantMapper, jwtTokenFactory);
        Tenant activeTenant = new Tenant();
        activeTenant.setId(TENANT_ID);
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(activeTenant);
        when(jwtTokenFactory.createAccessToken(any(User.class), any(UUID.class), any(Instant.class)))
                .thenReturn(TOKENS.accessToken());
        when(jwtTokenFactory.createRefreshToken(any(UUID.class), any(UUID.class), any(UUID.class), any(Instant.class)))
                .thenReturn(TOKENS.refreshToken());
        when(jwtTokenFactory.decodeRefreshToken(anyString()))
                .thenThrow(AuthenticationExceptionMapping.invalidRefreshToken());
        when(sessionMapper.revokeForRotation(any(UUID.class), any(Instant.class))).thenReturn(1);
    }

    private void validTokenFor(UserSession session, String token) {
        doReturn(new JwtTokenFactory.RefreshClaims(session.getUserId(), session.getId(), session.getFamilyId()))
                .when(jwtTokenFactory).decodeRefreshToken(token);
    }

    private User activeUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setEmail("ops@roseiot.io");
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthorizationVersion(2);
        return user;
    }

    private SecurityUser securityUser(User user) {
        return new SecurityUser(user, List.of(new SimpleGrantedAuthority(ActionCode.DEVICE_READ)));
    }

    @Test
    void shouldIssueTokensAndPersistOnlyTheRefreshTokenDigestWhenOpeningSession() {
        User user = activeUser();
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(sessionMapper.insert(any(UserSession.class))).thenAnswer(invocation -> {
            UserSession row = invocation.getArgument(0);
            assertThat(row.getTokenHash()).isEqualTo(TokenDigests.sha256Hex(TOKENS.refreshToken()));
            assertThat(row.getUserId()).isEqualTo(USER_ID);
            assertThat(row.getFamilyId()).isNotNull();
            assertThat(row.getExpiresAt()).isAfter(Instant.now());
            return 1;
        });

        assertThat(cut.openSession(securityUser(user))).isEqualTo(TOKENS);

        verify(tenantMapper).selectById(TENANT_ID);
        verify(sessionMapper).insert(any(UserSession.class));
    }

    @Test
    void shouldRejectRefreshTokenThatIsMissingOrBlank() {
        assertThatThrownBy(() -> cut.refresh(null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> cut.refresh("  ")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(sessionMapper);
    }

    @Test
    void shouldRotateAnActiveRefreshSession() {
        UserSession active = session(USER_ID, UUID.randomUUID(), UUID.randomUUID());
        validTokenFor(active, "presented-refresh-token");
        when(sessionMapper.selectOne(any())).thenReturn(active);
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser());

        assertThat(cut.refresh("presented-refresh-token")).isEqualTo(TOKENS);

        verify(sessionMapper).revokeForRotation(eq(active.getId()), any(Instant.class));
        verify(sessionMapper).insert(any(UserSession.class));
    }

    @Test
    void shouldRevokeFamilyWhenConcurrentRefreshLosesAtomicRotation() {
        UserSession active = session(USER_ID, UUID.randomUUID(), UUID.randomUUID());
        UserSession sibling = session(USER_ID, active.getFamilyId(), UUID.randomUUID());
        validTokenFor(active, "presented-refresh-token");
        when(sessionMapper.selectOne(any())).thenReturn(active);
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser());
        when(sessionMapper.revokeForRotation(any(UUID.class), any(Instant.class))).thenReturn(0);
        when(sessionMapper.selectList(any())).thenReturn(List.of(sibling));

        assertThatThrownBy(() -> cut.refresh("presented-refresh-token"))
                .isInstanceOf(RuntimeException.class);

        assertThat(sibling.getRevokedAt()).isNotNull();
        verify(sessionMapper).updateById(sibling);
    }

    @Test
    void shouldRejectOpeningSessionWhenTenantIsDisabled() {
        UUID tenantId = UUID.randomUUID();
        User tenantUser = activeUser();
        tenantUser.setTenantId(tenantId);
        SecurityUser principal = securityUser(tenantUser);
        Tenant disabled = new Tenant();
        disabled.setId(tenantId);
        disabled.setDisabledAt(Instant.now());
        when(userMapper.selectById(USER_ID)).thenReturn(tenantUser);
        when(tenantMapper.selectById(tenantId)).thenReturn(disabled);

        assertThatThrownBy(() -> cut.openSession(principal)).isInstanceOf(BadCredentialsException.class);
        verify(sessionMapper, never()).insert(any(UserSession.class));
    }

    @Test
    void shouldRevokeTheWholeFamilyWhenRefreshTokenIsReused() {
        UUID familyId = UUID.randomUUID();
        UserSession alreadyRotated = session(USER_ID, familyId, UUID.randomUUID());
        alreadyRotated.setRevokedAt(Instant.now().minusSeconds(60));
        validTokenFor(alreadyRotated, "stolen-refresh-token");
        when(sessionMapper.selectOne(any())).thenReturn(null, alreadyRotated);
        UserSession sibling = session(USER_ID, familyId, UUID.randomUUID());
        when(sessionMapper.selectList(any())).thenReturn(List.of(sibling));

        assertThatThrownBy(() -> cut.refresh("stolen-refresh-token")).isInstanceOf(RuntimeException.class);

        assertThat(sibling.getRevokedAt()).isNotNull();
        verify(sessionMapper).updateById(sibling);
    }

    @Test
    void shouldNotRevokeFamilyForNeverIssuedRefreshToken() {
        assertThatThrownBy(() -> cut.refresh("invented-refresh-token")).isInstanceOf(RuntimeException.class);
        verify(sessionMapper, never()).selectList(any());
    }

    @Test
    void shouldRevokeOnlyCurrentSessionOnLogout() {
        UUID sessionId = UUID.randomUUID();
        UserSession active = session(USER_ID, UUID.randomUUID(), sessionId);
        when(sessionMapper.selectById(sessionId)).thenReturn(active);

        cut.logoutCurrentSession(sessionId);

        assertThat(active.getRevokedAt()).isNotNull();
        verify(sessionMapper).updateById(active);
        verify(sessionMapper, never()).selectList(any());
    }

    @Test
    void shouldRevokeEverySessionOnLogoutAll() {
        UserSession first = session(USER_ID, UUID.randomUUID(), UUID.randomUUID());
        UserSession second = session(USER_ID, UUID.randomUUID(), UUID.randomUUID());
        when(sessionMapper.selectList(any())).thenReturn(List.of(first, second));

        cut.logoutAllSessions(USER_ID);

        assertThat(first.getRevokedAt()).isNotNull();
        assertThat(second.getRevokedAt()).isNotNull();
        verify(sessionMapper).updateById(first);
        verify(sessionMapper).updateById(second);
    }

    private static UserSession session(UUID userId, UUID familyId, UUID sessionId) {
        UserSession session = new UserSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setFamilyId(familyId);
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        return session;
    }
}
