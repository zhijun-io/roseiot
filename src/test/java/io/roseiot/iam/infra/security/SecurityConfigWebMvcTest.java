package io.roseiot.iam.infra.security;

import io.roseiot.iam.application.UserCredentialService;
import io.roseiot.iam.application.UserService;
import io.roseiot.iam.persistence.User;
import io.roseiot.iam.web.UserController;
import io.roseiot.iam.web.UserCredentialController;
import io.roseiot.tenant.application.TenantInvitationService;
import io.roseiot.tenant.web.TenantInvitationNoauthController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(controllers = {UserController.class, TenantInvitationNoauthController.class, UserCredentialController.class})
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
@Import(IdentityJwtAuthenticationConverter.class)
class SecurityConfigWebMvcTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    @Autowired private MockMvcTester mvc;
    @MockitoBean private TenantInvitationService tenantInvitationService;
    @MockitoBean private UserService userService;
    @MockitoBean private UserCredentialService credentialService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private SecurityUserDetailsService userDetailsService;
    @MockitoBean private AuthenticationManager identityAuthenticationManager;
    @MockitoBean private JwtTokenFactory jwtTokenFactory;

    @BeforeEach
    void setUpHandlers() {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setEmail("ops@roseiot.io");
        SecurityUser principal = new SecurityUser(user, List.of());
        given(identityAuthenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        given(jwtTokenFactory.createAccessToken(any(), any(), any())).willReturn("access-token");
        given(credentialService.checkResetToken(any())).willReturn(UserCredentialService.ResetTokenState.VALID);
        given(tenantInvitationService.acceptInvitation(any(), any())).willReturn("access-token");
        given(userService.getProfile(any())).willReturn(new UserService.UserProfile(USER_ID, "ops@roseiot.io", null));
    }

    @Test
    void allowsPublicTokenResourcesWithoutParsingAuthorizationHeader() {
        assertThat(mvc.get().uri("/password-resets/recovery-token")
                .header("Authorization", "Bearer invalid")).hasStatus2xxSuccessful();
    }

    @Test
    void allowsAnonymousLoginAndPasswordResetRequest() {
        assertThat(mvc.post().uri("/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ops@roseiot.io\",\"password\":\"correct horse\"}")).hasStatus2xxSuccessful();
        assertThat(mvc.post().uri("/password-resets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ops@roseiot.io\"}")).hasStatus2xxSuccessful();
    }

    @Test
    void allowsAnonymousInvitationAcceptance() {
        assertThat(mvc.put().uri("/tenant-invitations/invite-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"Passw0rd!\"}")).hasStatus2xxSuccessful();
    }

    @Test
    void requiresBearerTokenForProfile() {
        assertThat(mvc.get().uri("/me")).hasStatus(401);
        verifyNoInteractions(userService);
    }

    @Test
    void acceptsAuthenticatedProfileRequest() {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setEmail("ops@roseiot.io");
        assertThat(mvc.get().uri("/me").with(user(new SecurityUser(user, List.of())))).hasStatus(200);
    }

    @Test
    void deniesUnknownNoauthPathsAndRetiredApiPaths() {
        assertThat(mvc.get().uri("/noauth/not-a-public-route")).hasStatus(401);
        assertThat(mvc.get().uri("/api/login")).hasStatus(401);
    }

    @Test
    void deniesAuthenticatedCallersEverythingOutsideApi() {
        assertThat(mvc.get().uri("/api/devices").with(user("someone"))).hasStatus(404);
        assertThat(mvc.get().uri("/login").with(user("someone"))).hasStatus(403);
    }
}
