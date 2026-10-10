package io.roseiot.identity.application;

import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRefreshSession;
import io.roseiot.identity.persistence.UserRefreshSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserRefreshSessionsTest {

    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000003");
    private static final String ACCESS_TOKEN = "header.payload.signature";
    private static final Instant NOW = Instant.parse("2026-10-09T02:00:00Z");

    private UserRefreshSessionMapper refreshSessionMapper;
    private UserMapper userMapper;
    private JwtEncoder jwtEncoder;
    private UserRefreshSessions cut;

    @BeforeEach
    void setUp() {
        refreshSessionMapper = mock(UserRefreshSessionMapper.class);
        userMapper = mock(UserMapper.class);
        jwtEncoder = mock(JwtEncoder.class);
        cut = new UserRefreshSessions(refreshSessionMapper, userMapper, jwtEncoder);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt(ACCESS_TOKEN));
        when(refreshSessionMapper.revokeForRotation(any(UUID.class), any(Instant.class))).thenReturn(1);
    }

    private static Jwt jwt(String tokenValue) {
        Instant issuedAt = NOW;
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", USER_ID.toString());
        return new Jwt(tokenValue, issuedAt, issuedAt.plus(15, ChronoUnit.MINUTES),
                Map.of("alg", "HS256"), claims);
    }

    private User storedUser(int authorizationVersion, UUID tenantId) {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(tenantId);
        user.setAuthorizationVersion(authorizationVersion);
        return user;
    }

    private JwtEncoderParameters encodedParameters() {
        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());
        return captor.getValue();
    }

    private UserRefreshSession insertedSession() {
        ArgumentCaptor<UserRefreshSession> captor = ArgumentCaptor.forClass(UserRefreshSession.class);
        verify(refreshSessionMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldIssueAccessTokenAndOpaqueRefreshToken() {
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(4, null));

        UserSessionService.SessionTokens tokens = cut.issueNewFamily(USER_ID, NOW);

        assertThat(tokens.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(tokens.refreshToken()).hasSize(43).doesNotContain("=", "+", "/");
    }

    @Test
    void shouldStoreDigestNotThePlainTokenWithSevenDayExpiry() {
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(4, null));

        UserSessionService.SessionTokens tokens = cut.issueNewFamily(USER_ID, NOW);

        UserRefreshSession session = insertedSession();
        assertThat(session.getTokenHash()).isEqualTo(TokenDigests.sha256Hex(tokens.refreshToken()));
        assertThat(session.getExpiresAt()).isEqualTo(NOW.plus(7, ChronoUnit.DAYS));
        assertThat(session.getCreatedAt()).isEqualTo(NOW);
        assertThat(session.getUserId()).isEqualTo(USER_ID);
        assertThat(session.getRevokedAt()).isNull();
        assertThat(session.getId()).isNotEqualTo(session.getFamilyId());
    }

    @Test
    void shouldNotEmbedContextOrTenantIdClaims() {
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(4, UUID.randomUUID()));

        cut.issueNewFamily(USER_ID, NOW);

        Map<String, Object> claims = encodedParameters().getClaims().getClaims();
        assertThat(claims).doesNotContainKeys("ctx", "tid");
    }

    @Test
    void shouldCarrySubjectSessionAndAuthorizationVersionIntoTheToken() {
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(9, null));

        UserSessionService.SessionTokens tokens = cut.issueNewFamily(USER_ID, NOW);

        Map<String, Object> claims = encodedParameters().getClaims().getClaims();
        assertThat(claims)
                .containsEntry("sub", USER_ID.toString())
                .containsEntry(IdentityJwtClaims.AUTHORIZATION_VERSION, 9)
                .containsEntry("sid", insertedSession().getId().toString());
        assertThat(UUID.fromString((String) claims.get("sid"))).isNotNull();
        assertThat(tokens.accessToken()).isEqualTo(ACCESS_TOKEN);
    }

    @Test
    void shouldShortenAccessTokenLifetimeToFifteenMinutes() {
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(1, null));

        cut.issueNewFamily(USER_ID, NOW);

        assertThat(encodedParameters().getClaims().getExpiresAt())
                .isEqualTo(encodedParameters().getClaims().getIssuedAt().plus(15, ChronoUnit.MINUTES));
    }

    @Test
    void shouldSignWithConfiguredSharedSecretAlgorithm() {
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(1, null));

        cut.issueNewFamily(USER_ID, NOW);

        assertThat(encodedParameters().getJwsHeader().getAlgorithm()).isEqualTo(MacAlgorithm.HS256);
    }

    @Test
    void shouldFailWhenUserVanishedBeforeTokenIssuance() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> cut.issueNewFamily(USER_ID, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot issue access token for missing user");
    }

    @Test
    void shouldRevokeCurrentSessionAndContinueTheFamilyOnRotation() {
        UUID familyId = UUID.randomUUID();
        UserRefreshSession current = new UserRefreshSession();
        current.setId(UUID.randomUUID());
        current.setUserId(USER_ID);
        current.setFamilyId(familyId);
        current.setTokenHash(TokenDigests.sha256Hex("presented-refresh-token"));
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(4, null));

        UserSessionService.SessionTokens tokens = cut.rotate(current, NOW);

        verify(refreshSessionMapper).revokeForRotation(current.getId(), NOW);
        UserRefreshSession next = insertedSession();
        assertThat(next.getFamilyId()).isEqualTo(familyId);
        assertThat(next.getUserId()).isEqualTo(USER_ID);
        assertThat(next.getTokenHash()).isNotEqualTo(current.getTokenHash());
        assertThat(next.getTokenHash()).isEqualTo(TokenDigests.sha256Hex(tokens.refreshToken()));
    }

    @Test
    void shouldIgnoreLogoutWithoutSessionClaim() {
        cut.revokeSession(null, NOW);

        verifyNoInteractions(refreshSessionMapper);
    }

    @Test
    void shouldIgnoreLogoutForUnknownSession() {
        UUID sessionId = UUID.randomUUID();
        when(refreshSessionMapper.selectById(sessionId)).thenReturn(null);

        cut.revokeSession(sessionId, NOW);

        verify(refreshSessionMapper, never()).updateById(any(UserRefreshSession.class));
    }

    @Test
    void shouldNotRevokeAnAlreadyRevokedSessionTwice() {
        UUID sessionId = UUID.randomUUID();
        UserRefreshSession revoked = new UserRefreshSession();
        revoked.setId(sessionId);
        revoked.setRevokedAt(NOW.minus(1, ChronoUnit.MINUTES));
        when(refreshSessionMapper.selectById(sessionId)).thenReturn(revoked);

        cut.revokeSession(sessionId, NOW);

        verify(refreshSessionMapper, never()).updateById(any(UserRefreshSession.class));
        assertThat(revoked.getRevokedAt()).isEqualTo(NOW.minus(1, ChronoUnit.MINUTES));
    }

    @Test
    void shouldStampRevokedAtOnActiveSession() {
        UUID sessionId = UUID.randomUUID();
        UserRefreshSession active = new UserRefreshSession();
        active.setId(sessionId);
        when(refreshSessionMapper.selectById(sessionId)).thenReturn(active);

        cut.revokeSession(sessionId, NOW);

        verify(refreshSessionMapper).updateById(active);
        assertThat(active.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRevokeEveryActiveSessionOfTheUser() {
        UserRefreshSession first = new UserRefreshSession();
        first.setId(UUID.randomUUID());
        UserRefreshSession second = new UserRefreshSession();
        second.setId(UUID.randomUUID());
        when(refreshSessionMapper.selectList(any())).thenReturn(List.of(first, second));

        cut.revokeAllForUser(USER_ID, NOW);

        assertThat(first.getRevokedAt()).isEqualTo(NOW);
        assertThat(second.getRevokedAt()).isEqualTo(NOW);
        verify(refreshSessionMapper, times(2)).updateById(any(UserRefreshSession.class));
    }

    @Test
    void shouldRevokeEveryActiveSessionOfTheFamilyAfterTokenReuse() {
        UUID familyId = UUID.randomUUID();
        UserRefreshSession sibling = new UserRefreshSession();
        sibling.setId(UUID.randomUUID());
        sibling.setFamilyId(familyId);
        when(refreshSessionMapper.selectList(any())).thenReturn(List.of(sibling));

        cut.revokeFamily(familyId, NOW);

        assertThat(sibling.getRevokedAt()).isEqualTo(NOW);
        verify(refreshSessionMapper).updateById(sibling);
    }

    @Test
    void shouldWriteNothingWhenThereIsNoActiveSessionToRevoke() {
        when(refreshSessionMapper.selectList(any())).thenReturn(List.of());

        cut.revokeAllForUser(USER_ID, NOW);
        cut.revokeFamily(UUID.randomUUID(), NOW);

        verify(refreshSessionMapper, never()).updateById(any(UserRefreshSession.class));
    }
}
