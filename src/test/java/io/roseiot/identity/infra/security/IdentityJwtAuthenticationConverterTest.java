package io.roseiot.identity.infra.security;

import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.persistence.UserRefreshSession;
import io.roseiot.identity.persistence.UserRefreshSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class IdentityJwtAuthenticationConverterTest {

    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000004");
    private static final UUID SESSION_ID = UUID.fromString("019a0000-0000-7000-8000-000000000014");

    private SecurityUserDetailsService userDetailsService;
    private UserRefreshSessionMapper refreshSessionMapper;
    private IdentityJwtAuthenticationConverter cut;

    @BeforeEach
    void setUp() {
        userDetailsService = mock(SecurityUserDetailsService.class);
        refreshSessionMapper = mock(UserRefreshSessionMapper.class);
        cut = new IdentityJwtAuthenticationConverter(userDetailsService, refreshSessionMapper);
        when(refreshSessionMapper.selectActiveById(eq(SESSION_ID), any()))
                .thenReturn(activeSession(USER_ID));
    }

    private static Jwt token(String subject, Object authorizationVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("sid", SESSION_ID.toString());
        if (authorizationVersion != null) {
            claims.put(IdentityJwtClaims.AUTHORIZATION_VERSION, authorizationVersion);
        }
        Instant issuedAt = Instant.now();
        return new Jwt("raw-token-value", issuedAt, issuedAt.plus(15, ChronoUnit.MINUTES),
                Map.of("alg", "HS256"), claims);
    }

    private static UserRefreshSession activeSession(UUID userId) {
        UserRefreshSession session = new UserRefreshSession();
        session.setId(SESSION_ID);
        session.setUserId(userId);
        session.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        return session;
    }

    private SecurityUser loadedUser(long authorizationVersion) {
        return new SecurityUser(
                userEntity(authorizationVersion),
                List.of(new SimpleGrantedAuthority(IdentityJwtClaims.PERMISSION_PROFILE_READ)));
    }

    private static io.roseiot.identity.persistence.User userEntity(long authorizationVersion) {
        io.roseiot.identity.persistence.User user = new io.roseiot.identity.persistence.User();
        user.setId(USER_ID);
        user.setAuthorizationVersion(Math.toIntExact(authorizationVersion));
        user.setEmail("ops@roseiot.io");
        user.setPasswordHash("hash");
        return user;
    }

    private void assertInvalidToken(Runnable action, String description) {
        assertThatThrownBy(action::run)
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(exception -> ((OAuth2AuthenticationException) exception).getError().getErrorCode())
                .isEqualTo("invalid_token");
        assertThatThrownBy(action::run)
                .hasMessageContaining(description);
    }

    @Test
    void shouldAuthenticateActiveUserWithMatchingAuthorizationVersion() {
        when(userDetailsService.loadByUserId(USER_ID)).thenReturn(loadedUser(7L));

        Authentication authentication = cut.convert(token(USER_ID.toString(), 7L));

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo("ops@roseiot.io");
        assertThat(authentication.getPrincipal()).isInstanceOf(SecurityUser.class);
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        assertThat(user.userId()).isEqualTo(USER_ID);
        assertThat(user.authorizationVersion()).isEqualTo(7L);
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .contains(IdentityJwtClaims.PERMISSION_PROFILE_READ);
    }

    @Test
    void shouldAcceptAuthorizationVersionDeliveredAsInteger() {
        when(userDetailsService.loadByUserId(USER_ID)).thenReturn(loadedUser(3L));

        assertThat(cut.convert(token(USER_ID.toString(), 3))).isNotNull();
    }

    @Test
    void shouldRejectTokenMintedBeforeAuthorizationChanged() {
        when(userDetailsService.loadByUserId(USER_ID)).thenReturn(loadedUser(8L));

        assertInvalidToken(() -> cut.convert(token(USER_ID.toString(), 7L)),
                "Authorization version mismatch");
    }

    @Test
    void shouldRejectTokenForUnknownUser() {
        when(userDetailsService.loadByUserId(USER_ID)).thenThrow(new UsernameNotFoundException("missing"));

        assertInvalidToken(() -> cut.convert(token(USER_ID.toString(), 1L)), "User not active");
    }

    @Test
    void shouldRejectTokenWithoutAuthorizationVersionClaim() {
        when(userDetailsService.loadByUserId(USER_ID)).thenReturn(loadedUser(1L));

        assertInvalidToken(() -> cut.convert(token(USER_ID.toString(), null)),
                "Missing authorization version");
    }

    @Test
    void shouldRejectAuthorizationVersionThatIsNotANumber() {
        when(userDetailsService.loadByUserId(USER_ID)).thenReturn(loadedUser(1L));

        assertInvalidToken(() -> cut.convert(token(USER_ID.toString(), "1")),
                "Missing authorization version");
    }

    @Test
    void shouldRejectSubjectThatIsNotAUserId() {
        assertInvalidToken(() -> cut.convert(token("session-scoped-principal", 1L)), "Invalid subject");
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void shouldRejectTokenWithoutSessionClaim() {
        when(userDetailsService.loadByUserId(USER_ID)).thenReturn(loadedUser(1));
        Jwt token = token(USER_ID.toString(), 1L);
        Map<String, Object> claims = new HashMap<>(token.getClaims());
        claims.remove("sid");
        Jwt withoutSession = new Jwt(token.getTokenValue(), token.getIssuedAt(), token.getExpiresAt(),
                token.getHeaders(), claims);

        assertInvalidToken(() -> cut.convert(withoutSession), "Invalid session");
    }

    @Test
    void shouldRejectTokenForMissingOrInactiveSession() {
        when(userDetailsService.loadByUserId(USER_ID)).thenReturn(loadedUser(1));
        when(refreshSessionMapper.selectActiveById(eq(SESSION_ID), any())).thenReturn(null);

        assertInvalidToken(() -> cut.convert(token(USER_ID.toString(), 1L)), "Session not active");
    }

    @Test
    void shouldRejectSessionOwnedByAnotherUser() {
        when(userDetailsService.loadByUserId(USER_ID)).thenReturn(loadedUser(1));
        when(refreshSessionMapper.selectActiveById(eq(SESSION_ID), any()))
                .thenReturn(activeSession(UUID.randomUUID()));

        assertInvalidToken(() -> cut.convert(token(USER_ID.toString(), 1L)), "Session not active");
    }
}
