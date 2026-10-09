package io.roseiot.identity.web;

import io.roseiot.identity.application.UserRecoveryService;
import io.roseiot.identity.application.UserSessionService;
import io.roseiot.identity.domain.InvalidCredentialsException;
import io.roseiot.identity.domain.InvalidRecoveryTokenException;
import io.roseiot.identity.domain.InvalidRefreshTokenException;
import io.roseiot.identity.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.identity.infra.security.JsonSecurityHandlers;
import io.roseiot.identity.infra.security.SecurityConfig;
import io.roseiot.shared.api.ApiErrorBodyWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
 * Protocol contract of {@code /api/auth/**}: routing, JSON shape, input validation and the stable
 * error bodies. Requests run through the production {@link SecurityConfig} chain, because all four
 * endpoints under test are public; no filter is switched off to make a request reach the handler.
 *
 * <p>The rate-limit filter stays out of this slice on purpose. Its own unit test proves the limit,
 * and the full filter order belongs to an integration test against the real application context.
 */
@WebMvcTest(controllers = AuthController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@Import({SecurityConfig.class, JsonSecurityHandlers.class, ApiErrorBodyWriter.class})
class AuthControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("019f0000-0000-7000-8000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("019f0000-0000-7000-8000-000000000002");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserSessionService sessionService;

    @MockitoBean
    private UserRecoveryService recoveryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldAnswerLoginWithBothTokens() {
        given(sessionService.login("ops@roseiot.io", "correct horse"))
                .willReturn(new UserSessionService.SessionTokens("access-token-value", "refresh-token-value"));

        assertThat(mvc.post().uri("/api/auth/login")
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
        willThrow(new InvalidCredentialsException()).given(sessionService).login(any(), any());

        assertThat(mvc.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "ops@roseiot.io", "password": "wrong password"}
                        """))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "code": "invalid_credentials",
                          "message": "Invalid email or password"
                        }
                        """);
    }

    @Test
    void shouldRejectLoginWithBlankPasswordBeforeReachingTheService() {
        assertThat(mvc.post().uri("/api/auth/login")
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
        assertThat(mvc.post().uri("/api/auth/login")
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

        assertThat(mvc.post().uri("/api/auth/token")
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
        willThrow(new InvalidRefreshTokenException()).given(sessionService).refresh(any());

        assertThat(mvc.post().uri("/api/auth/token")
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
    void shouldAnswerRecoveryRequestWithoutRevealingWhetherTheEmailExists() {
        given(recoveryService.requestRecovery("nobody@roseiot.io"))
                .willReturn(UserRecoveryService.ACCEPTED_MESSAGE);

        assertThat(mvc.post().uri("/api/auth/recovery/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "nobody@roseiot.io"}
                        """))
                .hasStatus(HttpStatus.ACCEPTED)
                .bodyJson()
                .extractingPath("$.message")
                .isEqualTo(UserRecoveryService.ACCEPTED_MESSAGE);
    }

    @Test
    void shouldRejectRecoveryRequestWithBlankEmail() {
        assertThat(mvc.post().uri("/api/auth/recovery/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": ""}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("validation_error");

        verifyNoInteractions(recoveryService);
    }

    @Test
    void shouldAnswerValidRecoveryTokenWithNoContentAndTheNewPassword() {
        assertThat(mvc.post().uri("/api/auth/recovery/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "recovery-token", "newPassword": "Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(recoveryService).confirmRecovery("recovery-token", "Passw0rd!");
    }

    @Test
    void shouldAnswerExpiredRecoveryTokenWithUnauthorized() {
        willThrow(new InvalidRecoveryTokenException()).given(recoveryService).confirmRecovery(any(), any());

        assertThat(mvc.post().uri("/api/auth/recovery/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "used-recovery-token", "newPassword": "Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invalid_recovery_token");
    }

    @Test
    void shouldRevokeOnlyTheSessionOfThePresentedToken() {
        assertThat(mvc.post().uri("/api/auth/logout")
                .with(jwt().jwt(this::bearerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(sessionService).logoutCurrentSession(SESSION_ID);
    }

    @Test
    void shouldRevokeEverySessionOfTheAuthenticatedUser() {
        assertThat(mvc.post().uri("/api/auth/logout-all")
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
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}
