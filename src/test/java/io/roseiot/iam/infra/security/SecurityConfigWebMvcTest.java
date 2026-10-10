package io.roseiot.iam.infra.security;

import io.roseiot.iam.domain.ActionCode;
import io.roseiot.iam.application.UserService;
import io.roseiot.iam.application.UserCredentialService;
import io.roseiot.iam.application.UserSessionService;
import io.roseiot.iam.persistence.User;
import io.roseiot.iam.persistence.UserSession;
import io.roseiot.iam.persistence.UserSessionMapper;
import io.roseiot.iam.persistence.UserMapper;
import io.roseiot.tenant.application.TenantInvitationService;
import io.roseiot.iam.infra.security.JwtTokenFactory;
import io.roseiot.iam.web.UserController;
import io.roseiot.iam.web.UserController;
import io.roseiot.iam.web.UserCredentialController;
import io.roseiot.tenant.web.TenantInvitationNoauthController;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

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
@WebMvcTest(controllers = {UserController.class, TenantInvitationNoauthController.class,
        UserController.class, UserCredentialController.class})
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
@Import(IdentityJwtAuthenticationConverter.class)
class SecurityConfigWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("019f0000-0000-7000-8000-000000000001");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserSessionService sessionService;

    @MockitoBean
    private TenantInvitationService tenantInvitationService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserCredentialService credentialService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private SecurityUserDetailsService userDetailsService;

    @MockitoBean
    private UserSessionMapper refreshSessionMapper;

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
        given(credentialService.checkResetToken(any()))
                .willReturn(UserCredentialService.ResetTokenState.VALID);
        given(tenantInvitationService.acceptInvitation(any(), any()))
                .willReturn(new UserSessionService.SessionTokens("access-token", "refresh-token"));
        given(userService.getProfile(any()))
                .willReturn(new UserService.UserProfile(USER_ID, "ops@roseiot.io"));
    }

    @Test
    void shouldLetAnonymousCallersGetPasswordResetCheckWithInvalidBearer() {
        assertThat(mvc.get()
                        .uri("/noauth/user-credentials/password-resets?resetToken=recovery-token")
                        .header("Authorization", "Bearer not-a-valid-jwt"))
                .hasStatus(HttpStatus.OK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/token", "/noauth/user-credentials/password-resets", "/noauth/tenants/invitations"})
    void shouldLetAnonymousCallersUseThePublicAuthEndpoints(String path) {
        String body =
                switch (path) {
                    case "/login" -> """
                            {"email": "ops@roseiot.io", "password": "correct horse"}""";
                    case "/token" -> """
                            {"refreshToken": "token"}""";
                    case "/noauth/user-credentials/password-resets" -> """
                            {"email": "ops@roseiot.io"}""";
                    case "/noauth/tenants/invitations" -> """
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

        verifyNoInteractions(userService);
    }

    @Test
    void shouldAcceptBearerTokenForTheProfile() {
        User userRecord = new User();
        userRecord.setId(USER_ID);
        userRecord.setEmail("ops@roseiot.io");
        SecurityUser principal = new SecurityUser(
                userRecord, List.of(RoseiotWebMvcTestSupport.platformAdminAuthorities()));

        assertThat(mvc.get().uri("/api/users/me").with(user(principal)))
                .hasStatus(HttpStatus.OK);
    }

    @Test
    void shouldRequireBearerTokenForLogout() {
        assertThat(mvc.delete().uri("/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(sessionService);
    }

    @Test
    void shouldAcceptBearerTokenForLogout() {
        assertThat(mvc.delete().uri("/logout")
                .with(jwt().jwt(this::systemToken).authorities(RoseiotWebMvcTestSupport.platformAdminAuthorities())))
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
    void shouldDenyUnknownNoauthPaths() {
        assertThat(mvc.get().uri("/noauth/not-a-public-route"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldRequireAuthenticationForRetiredAnonymousApiPaths() {
        for (String path : List.of("/api/login", "/api/token", "/api/noauth/user-credentials/password-resets")) {
            assertThat(mvc.get().uri(path))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void shouldInvalidateTheSameAccessTokenImmediatelyAfterLogout() {
        UUID sessionId = UUID.randomUUID();
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(UUID.fromString("019a0000-0000-7000-8000-0000000000f1"));
        user.setEmail("ops@roseiot.io");
        user.setAuthorizationVersion(1);
        SecurityUser principal = new SecurityUser(user, List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        ActionCode.DEVICE_READ)));
        UserSession session = new UserSession();
        session.setId(sessionId);
        session.setUserId(USER_ID);
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        boolean[] revoked = {false};
        when(userDetailsService.loadByUserId(USER_ID)).thenReturn(principal);
        when(refreshSessionMapper.selectActiveById(eq(sessionId), any()))
                .thenAnswer(invocation -> revoked[0] ? null : session);
        doAnswer(invocation -> {
                    revoked[0] = true;
                    return null;
                })
                .when(sessionService)
                .logoutCurrentSession(sessionId);

        Instant now = Instant.now();
        Jwt token = Jwt.withTokenValue("same-access-token")
                .header("alg", "HS256")
                .subject(USER_ID.toString())
                .claim("sid", sessionId.toString())
                .claim("tid", user.getTenantId().toString())
                .claim(JwtTokenFactory.AUTHORIZATION_VERSION, 1)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .build();
        given(jwtDecoder.decode("same-access-token")).willReturn(token);

        assertThat(mvc.get().uri("/api/users/me")
                        .header("Authorization", "Bearer same-access-token"))
                .hasStatus(HttpStatus.OK);
        assertThat(mvc.delete().uri("/logout")
                        .header("Authorization", "Bearer same-access-token"))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/users/me")
                        .header("Authorization", "Bearer same-access-token"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldDenyAuthenticatedCallersEverythingElse() {
        assertThat(mvc.get().uri("/api/devices").with(jwt().jwt(this::systemToken)))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldNotPublishAuthEndpointsBeyondTheirMethod() {
        assertThat(mvc.get().uri("/login").with(jwt().jwt(this::systemToken)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    private Jwt.Builder systemToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(USER_ID.toString())
                .claim("sid", UUID.randomUUID().toString())
                .claim(JwtTokenFactory.AUTHORIZATION_VERSION, 1)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}
