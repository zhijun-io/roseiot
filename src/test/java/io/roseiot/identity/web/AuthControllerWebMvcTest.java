package io.roseiot.identity.web;

import io.roseiot.identity.application.UserSessionService;
import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.infra.security.AuthenticationExceptionMapping;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import io.roseiot.identity.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.identity.infra.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.identity.persistence.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Protocol contract of session endpoints under {@code /api/login}, {@code /api/token},
 * {@code /api/logout} and {@code /api/logout-all}.
 */
@WebMvcTest(controllers = AuthController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class AuthControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("019f0000-0000-7000-8000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("019f0000-0000-7000-8000-000000000002");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserSessionService sessionService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private UserRoleMapper userRoleMapper;

    @Test
    void shouldAnswerLoginWithBothTokens() {
        given(sessionService.login("ops@roseiot.io", "correct horse"))
                .willReturn(new UserSessionService.SessionTokens("access-token-value", "refresh-token-value"));

        assertThat(mvc.post().uri("/api/login")
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

        verify(sessionService).login("ops@roseiot.io", "correct horse");
    }

    @Test
    void shouldAnswerWrongCredentialsWithOneStableUnauthorizedBody() {
        willThrow(AuthenticationExceptionMapping.invalidCredentials()).given(sessionService).login(any(), any());

        assertThat(mvc.post().uri("/api/login")
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
    }

    @Test
    void shouldRejectLoginWithBlankPasswordBeforeReachingTheService() {
        assertThat(mvc.post().uri("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "ops@roseiot.io", "password": " "}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("validation_error");

        verifyNoInteractions(sessionService);
    }

    @Test
    void shouldRejectMalformedLoginBodyWithoutEchoingTheParserFailure() {
        assertThat(mvc.post().uri("/api/login")
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

        verifyNoInteractions(sessionService);
    }

    @Test
    void shouldExchangeRefreshTokenForNewTokens() {
        given(sessionService.refresh("rotating-refresh-token"))
                .willReturn(new UserSessionService.SessionTokens("new-access-token", "new-refresh-token"));

        assertThat(mvc.post().uri("/api/token")
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

        assertThat(mvc.post().uri("/api/token")
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
        assertThat(mvc.post().uri("/api/logout")
                .with(jwt().jwt(this::bearerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(sessionService).logoutCurrentSession(SESSION_ID);
    }

    @Test
    void shouldRevokeEverySessionOfTheAuthenticatedUser() {
        assertThat(mvc.post().uri("/api/logout-all")
                .with(jwt().jwt(this::bearerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(sessionService).logoutAllSessions(USER_ID);
    }

    private void bearerToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        builder.header("alg", "none")
                .subject(USER_ID.toString())
                .claim("sid", SESSION_ID.toString())
                .claim(IdentityJwtClaims.CONTEXT, IdentityJwtClaims.CONTEXT_SYSTEM)
                .claim(IdentityJwtClaims.AUTHORIZATION_VERSION, 1)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}
