package io.roseiot.iam.web;

import io.roseiot.iam.application.UserSessionService;
import io.roseiot.iam.application.UserService;
import io.roseiot.iam.infra.security.JwtTokenFactory;
import io.roseiot.iam.infra.security.AuthenticationExceptionMapping;
import io.roseiot.iam.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.iam.infra.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.iam.infra.security.SecurityUser;
import io.roseiot.iam.persistence.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Protocol contract of session endpoints under {@code /login}, {@code /token},
 * {@code /logout} and {@code /logout?all=true} (served by security filters).
 */
@WebMvcTest(controllers = UserController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class SessionAuthWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("019f0000-0000-7000-8000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("019f0000-0000-7000-8000-000000000002");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserSessionService sessionService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthenticationManager identityAuthenticationManager;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldAnswerLoginWithBothTokens() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("ops@roseiot.io");
        SecurityUser principal = new SecurityUser(user, List.of());
        given(identityAuthenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        given(sessionService.openSession(principal))
                .willReturn(new UserSessionService.SessionTokens("access-token-value", "refresh-token-value"));

        assertThat(mvc.post().uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "ops@roseiot.io", "password": "correct horse"}
                        """))
                .hasStatus(HttpStatus.OK)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "accessToken": "access-token-value",
                          "refreshToken": "refresh-token-value"
                        }
                        """);

        verify(sessionService).openSession(principal);
    }

    @Test
    void shouldAnswerWrongCredentialsWithOneStableUnauthorizedBody() {
        willThrow(AuthenticationExceptionMapping.invalidCredentials())
                .given(identityAuthenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThat(mvc.post().uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "ops@roseiot.io", "password": "wrong password"}
                        """))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "code": "invalid_credentials",
                          "message": "invalid_credentials"
                        }
                        """);

        verifyNoInteractions(sessionService);
    }

    @Test
    void shouldRejectLoginWithBlankPasswordBeforeReachingTheService() {
        assertThat(mvc.post().uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "ops@roseiot.io", "password": " "}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("validation_error");

        verifyNoInteractions(sessionService, identityAuthenticationManager);
    }

    @Test
    void shouldRejectMalformedLoginBodyWithoutEchoingTheParserFailure() {
        assertThat(mvc.post().uri("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "ops@roseiot.io",
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "code": "bad_request",
                          "message": "Malformed request body"
                        }
                        """);

        verifyNoInteractions(sessionService, identityAuthenticationManager);
    }

    @Test
    void shouldExchangeRefreshTokenForNewTokens() {
        given(sessionService.refresh("rotating-refresh-token"))
                .willReturn(new UserSessionService.SessionTokens("new-access-token", "new-refresh-token"));

        assertThat(mvc.post().uri("/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "rotating-refresh-token"}
                        """))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.refreshToken")
                .isEqualTo("new-refresh-token");
    }

    @Test
    void shouldAnswerReplayedRefreshTokenWithItsOwnCode() {
        willThrow(AuthenticationExceptionMapping.invalidRefreshToken()).given(sessionService).refresh(any());

        assertThat(mvc.post().uri("/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "already-rotated-token"}
                        """))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_refresh_token");
    }

    @Test
    void shouldRevokeOnlyTheSessionOfThePresentedToken() {
        assertThat(mvc.delete().uri("/logout")
                .with(jwt().jwt(this::bearerToken))
                )
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(sessionService).logoutCurrentSession(SESSION_ID);
    }

    @Test
    void shouldRevokeEverySessionOfTheAuthenticatedUser() {
        assertThat(mvc.delete().uri("/logout?all=true")
                .with(jwt().jwt(this::bearerToken))
                )
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(sessionService).logoutAllSessions(USER_ID);
    }

    private void bearerToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        builder.header("alg", "none")
                .subject(USER_ID.toString())
                .claim("sid", SESSION_ID.toString())
                .claim(JwtTokenFactory.AUTHORIZATION_VERSION, 1)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}
