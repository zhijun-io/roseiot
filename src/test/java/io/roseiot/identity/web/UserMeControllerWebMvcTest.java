package io.roseiot.identity.web;

import io.roseiot.identity.application.AuthorizationService;
import io.roseiot.identity.application.UserProfileService;
import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.domain.PasswordValidationException;
import io.roseiot.identity.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.identity.infra.security.JsonSecurityHandlers;
import io.roseiot.identity.infra.security.SecurityConfig;
import io.roseiot.shared.api.ApiErrorBodyWriter;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Protocol contract of {@code /api/users/me/**}: the principal is taken from the bearer token, the
 * permission decision is delegated to the application layer, and a rejected password is reported
 * through the shared error body. The filter chain is the production one; only the token converter
 * and the services are replaced, so this slice never touches the database.
 */
@WebMvcTest(controllers = UserMeController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@Import({SecurityConfig.class, JsonSecurityHandlers.class, ApiErrorBodyWriter.class})
class UserMeControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("019f0000-0000-7000-8000-000000000001");
    private static final long AUTHORIZATION_VERSION = 3L;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserProfileService accountService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @BeforeEach
    void grantTheTestPrincipalEveryPermission() {
        given(authorizationService.hasPermission(any(), anyLong(), anyString())).willReturn(true);
    }

    @Test
    void shouldAnswerProfileWithIdAndEmailOfTheTokenSubject() {
        given(accountService.getProfile(USER_ID)).willReturn(
                new UserProfileService.UserProfile(USER_ID, "ops@roseiot.io"));

        assertThat(mvc.get().uri("/api/users/me").with(jwt().jwt(this::systemToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "id": "019f0000-0000-7000-8000-000000000001",
                          "email": "ops@roseiot.io"
                        }
                        """);
    }

    @Test
    void shouldRejectPrincipalOutsideTheSystemContextBeforeAskingForPermission() {
        assertThat(mvc.get().uri("/api/users/me")
                .with(jwt().jwt(builder -> systemToken(builder).claim(IdentityJwtClaims.CONTEXT,
                        IdentityJwtClaims.CONTEXT_TENANT))))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("forbidden");

        verifyNoInteractions(accountService, authorizationService);
    }

    @Test
    void shouldRejectPrincipalWhenThePermissionIsMissing() {
        given(authorizationService.hasPermission(USER_ID, AUTHORIZATION_VERSION,
                IdentityJwtClaims.PERMISSION_PROFILE_READ)).willReturn(false);

        assertThat(mvc.get().uri("/api/users/me").with(jwt().jwt(this::systemToken)))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("forbidden");

        verifyNoInteractions(accountService);
    }

    @Test
    void shouldRejectTokenWithoutAuthorizationVersionClaim() {
        assertThat(mvc.get().uri("/api/users/me")
                .with(jwt().jwt(builder -> {
                    systemToken(builder);
                    builder.claims(claims -> claims.remove(IdentityJwtClaims.AUTHORIZATION_VERSION));
                })))
                .hasStatus(HttpStatus.FORBIDDEN);

        verifyNoInteractions(accountService, authorizationService);
    }

    @Test
    void shouldAnswerPasswordChangeWithNoContent() {
        assertThat(mvc.patch().uri("/api/users/me/password")
                .with(jwt().jwt(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "old-Passw0rd!", "newPassword": "new-Passw0rd!"}
                        """))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(accountService).changePassword(USER_ID, "old-Passw0rd!", "new-Passw0rd!");
    }

    @Test
    void shouldRejectPasswordChangeWithBlankNewPasswordBeforeReachingTheService() {
        assertThat(mvc.patch().uri("/api/users/me/password")
                .with(jwt().jwt(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "old-Passw0rd!", "newPassword": ""}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("validation_error");

        verifyNoInteractions(accountService);
    }

    @Test
    void shouldAnswerRejectedNewPasswordWithTheRuleThatFailed() {
        willThrow(new PasswordValidationException("Password must be at least 12 characters"))
                .given(accountService).changePassword(any(), any(), any());

        assertThat(mvc.patch().uri("/api/users/me/password")
                .with(jwt().jwt(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "old-Passw0rd!", "newPassword": "short"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "code": "validation_error",
                          "message": "Password must be at least 12 characters"
                        }
                        """);
    }

    private Jwt.Builder systemToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(USER_ID.toString())
                .claim(IdentityJwtClaims.CONTEXT, IdentityJwtClaims.CONTEXT_SYSTEM)
                .claim(IdentityJwtClaims.AUTHORIZATION_VERSION, Math.toIntExact(AUTHORIZATION_VERSION))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}
