package io.roseiot.identity.infra.security;

import io.roseiot.identity.application.UserProfileService;
import io.roseiot.identity.application.PasswordResetService;
import io.roseiot.identity.application.UserSessionService;
import io.roseiot.identity.persistence.User;
import io.roseiot.identity.persistence.UserMapper;
import io.roseiot.tenant.application.TenantInvitationService;
import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.web.NoauthController;
import io.roseiot.identity.web.UserProfileController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * The URL rules of the production {@link SecurityConfig}: which paths anonymous callers may reach,
 * which ones need a bearer token, and the default-deny for everything else. The chain is imported
 * explicitly: without it the slice falls back to Boot's default chain, which requires an
 * authenticated principal for every request and would silently hide a missing {@code permitAll} or
 * {@code denyAll} rule.
 *
 * <p>Each rule is asserted as a pair — a lonely success response proves nothing about authorization.
 * The {@code jwt()} post-processor supplies the principal itself, so the real
 * {@link IdentityJwtAuthenticationConverter} is not on this path: it is mocked to satisfy the chain
 * and its own rules are proven by {@code IdentityJwtAuthenticationConverterTest}.
 */
@WebMvcTest(controllers = {NoauthController.class, UserProfileController.class})
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class SecurityConfigWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("019f0000-0000-7000-8000-000000000001");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserSessionService sessionService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private TenantInvitationService tenantInvitationService;

    @MockitoBean
    private UserProfileService accountService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @MockitoBean
    private AuthenticationManager identityAuthenticationManager;

    @BeforeEach
    void makeTheHandlersBehindTheChainAnswer() {
        User stub = new User();
        stub.setId(USER_ID);
        stub.setEmail("ops@roseiot.io");
        SecurityUser principal = new SecurityUser(stub, List.of());
        given(identityAuthenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        given(sessionService.openSession(any(SecurityUser.class)))
                .willReturn(new UserSessionService.SessionTokens("access-token", "refresh-token"));
        given(sessionService.refresh(any()))
                .willReturn(new UserSessionService.SessionTokens("access-token", "refresh-token"));
        given(passwordResetService.checkResetToken(any()))
                .willReturn(PasswordResetService.ResetTokenState.VALID);
        given(tenantInvitationService.acceptInvitation(any(), any()))
                .willReturn(new UserSessionService.SessionTokens("access-token", "refresh-token"));
        given(accountService.getProfile(any()))
                .willReturn(new UserProfileService.UserProfile(USER_ID, "ops@roseiot.io"));
    }

    @Test
    void shouldLetAnonymousCallersGetPasswordResetCheckWithInvalidBearer() {
        assertThat(mvc.get()
                        .uri("/api/noauth/password-reset?resetToken=recovery-token")
                        .header("Authorization", "Bearer not-a-valid-jwt"))
                .hasStatus(HttpStatus.OK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/login", "/api/token", "/api/noauth/password-reset/email",
            "/api/noauth/password-reset", "/api/noauth/tenant-invitations"})
    void shouldLetAnonymousCallersUseThePublicAuthEndpoints(String path) {
        String body =
                switch (path) {
                    case "/api/login" -> """
                            {"email": "ops@roseiot.io", "password": "correct horse"}""";
                    case "/api/token" -> """
                            {"refreshToken": "token"}""";
                    case "/api/noauth/password-reset/email" -> """
                            {"email": "ops@roseiot.io"}""";
                    case "/api/noauth/password-reset" -> """
                            {"resetToken": "recovery-token", "password": "Passw0rd!"}""";
                    case "/api/noauth/tenant-invitations" -> """
                            {"invitationToken": "invite-token", "password": "Passw0rd!"}""";
                    default -> throw new IllegalArgumentException(path);
                };
        assertThat(mvc.post().uri(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus2xxSuccessful();
    }

    @Test
    void shouldRequireBearerTokenForTheProfile() {
        assertThat(mvc.get().uri("/api/users/me"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "code": "unauthorized",
                          "message": "unauthorized"
                        }
                        """);

        verifyNoInteractions(accountService);
    }

    @Test
    void shouldAcceptBearerTokenForTheProfile() {
        assertThat(mvc.get().uri("/api/users/me").with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken)))
                .hasStatus(HttpStatus.OK);
    }

    @Test
    void shouldRequireBearerTokenForLogout() {
        assertThat(mvc.post().uri("/api/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(sessionService);
    }

    @Test
    void shouldAcceptBearerTokenForLogout() {
        assertThat(mvc.post().uri("/api/logout")
                .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void shouldDenyAnonymousCallersEverythingElse() {
        assertThat(mvc.get().uri("/api/devices"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("unauthorized");
    }

    @Test
    void shouldDenyAuthenticatedCallersEverythingElse() {
        assertThat(mvc.get().uri("/api/devices").with(jwt().jwt(this::systemToken)))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "code": "forbidden",
                          "message": "Forbidden"
                        }
                        """);
    }

    @Test
    void shouldNotPublishAuthEndpointsBeyondTheirMethod() {
        assertThat(mvc.get().uri("/api/login").with(jwt().jwt(this::systemToken)))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private Jwt.Builder systemToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(USER_ID.toString())
                .claim("sid", UUID.randomUUID().toString())
                .claim(IdentityJwtClaims.AUTHORIZATION_VERSION, 1)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}
