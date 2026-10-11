package io.roseiot.iam.security;

import io.roseiot.iam.user.UserCredentialService;
import io.roseiot.iam.user.UserService;
import io.roseiot.iam.user.UserProfile;
import io.roseiot.iam.user.User;
import io.roseiot.iam.user.UserStatus;
import io.roseiot.tenant.TenantService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
@Import(JwtAuthenticationConverter.class)
class WebSecurityConfigWebMvcTest {
    private static final Long USER_ID = System.nanoTime();
    private static final Long TENANT_ID = 42L;
    @Autowired private MockMvcTester mvc;
    @MockitoBean private UserService userService;
    @MockitoBean private UserCredentialService credentialService;
    @MockitoBean private TenantService tenantService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private SecurityUserDetailsService userDetailsService;
    @MockitoBean private AuthenticationManager authenticationManager;
    @MockitoBean private JwtTokenFactory jwtTokenFactory;

    @BeforeEach
    void setUpHandlers() {
        User user = new User(USER_ID, TENANT_ID, "ops@roseiot.io", null, UserStatus.ACTIVE);
        SecurityUser principal = new SecurityUser(user, null, List.of());
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        given(jwtTokenFactory.createAccessToken(any(), any(), any())).willReturn("access-token");
        given(credentialService.checkResetToken(any())).willReturn(UserCredentialService.ResetTokenState.VALID);
        given(userService.getProfile(any())).willReturn(new UserProfile(USER_ID, "ops@roseiot.io", null));
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
    void requiresBearerTokenForProfile() {
        assertThat(mvc.get().uri("/me")).hasStatus(401);
        verifyNoInteractions(userService);
    }

    @Test
    void acceptsAuthenticatedProfileRequest() {
        User user = new User(USER_ID, TENANT_ID, "ops@roseiot.io", null, UserStatus.ACTIVE);
        assertThat(mvc.get().uri("/me").with(user(new SecurityUser(user, null, List.of())))).hasStatus(200);
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
