package io.roseiot.identity.infra.security;

import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdentityJwtAuthenticationConverterTest {

    private static final UUID USER_ID = UUID.fromString("019a0000-0000-7000-8000-000000000004");

    private UserMapper userMapper;
    private IdentityJwtAuthenticationConverter cut;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        cut = new IdentityJwtAuthenticationConverter(userMapper);
    }

    private static Jwt token(String subject, Object authorizationVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put(IdentityJwtClaims.CONTEXT, IdentityJwtClaims.CONTEXT_SYSTEM);
        if (authorizationVersion != null) {
            claims.put(IdentityJwtClaims.AUTHORIZATION_VERSION, authorizationVersion);
        }
        Instant issuedAt = Instant.now();
        return new Jwt("raw-token-value", issuedAt, issuedAt.plus(15, ChronoUnit.MINUTES),
                Map.of("alg", "HS256"), claims);
    }

    private User storedUser(int authorizationVersion) {
        User user = new User();
        user.setId(USER_ID);
        user.setAuthorizationVersion(authorizationVersion);
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
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(7));

        JwtAuthenticationToken authentication =
                (JwtAuthenticationToken) cut.convert(token(USER_ID.toString(), 7L));

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo(USER_ID.toString());
        assertThat(authentication.getTokenAttributes())
                .containsEntry(IdentityJwtClaims.CONTEXT, IdentityJwtClaims.CONTEXT_SYSTEM);
    }

    @Test
    void shouldAcceptAuthorizationVersionDeliveredAsInteger() {
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(3));

        // JSON numbers arrive as Integer or Long depending on the decoder; both must compare equal.
        assertThat(cut.convert(token(USER_ID.toString(), 3))).isNotNull();
    }

    @Test
    void shouldRejectTokenMintedBeforeAuthorizationChanged() {
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(8));

        assertInvalidToken(() -> cut.convert(token(USER_ID.toString(), 7L)),
                "Authorization version mismatch");
    }

    @Test
    void shouldRejectTokenForUnknownUser() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertInvalidToken(() -> cut.convert(token(USER_ID.toString(), 1L)), "User not active");
    }

    @Test
    void shouldRejectTokenForDisabledUser() {
        User disabled = storedUser(1);
        disabled.setDisabledAt(Instant.now());
        when(userMapper.selectById(USER_ID)).thenReturn(disabled);

        assertInvalidToken(() -> cut.convert(token(USER_ID.toString(), 1L)), "User not active");
    }

    @Test
    void shouldRejectTokenWithoutAuthorizationVersionClaim() {
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(1));

        assertInvalidToken(() -> cut.convert(token(USER_ID.toString(), null)),
                "Missing authorization version");
    }

    @Test
    void shouldRejectAuthorizationVersionThatIsNotANumber() {
        when(userMapper.selectById(USER_ID)).thenReturn(storedUser(1));

        assertInvalidToken(() -> cut.convert(token(USER_ID.toString(), "1")),
                "Missing authorization version");
    }

    @Test
    void shouldRejectSubjectThatIsNotAUserId() {
        assertInvalidToken(() -> cut.convert(token("session-scoped-principal", 1L)), "Invalid subject");
        verifyNoInteractions(userMapper);
    }
}
