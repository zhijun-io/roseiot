package io.roseiot.iam.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import io.roseiot.iam.resource.ResourceService;
import io.roseiot.iam.role.RoleService;
import io.roseiot.iam.user.User;
import io.roseiot.iam.user.UserCredentialService;
import io.roseiot.iam.user.UserService;
import io.roseiot.iam.user.UserStatus;
import io.roseiot.shared.security.JwtAuthenticationConverter;
import io.roseiot.shared.security.JwtTokenFactory;
import io.roseiot.shared.security.SecurityUser;
import io.roseiot.system.SystemSettingService;
import io.roseiot.tenant.TenantService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
@Import(JwtAuthenticationConverter.class)
class WebSecurityConfigWebMvcTest {
    private static final Long USER_ID = System.nanoTime();
    private static final Long TENANT_ID = 42L;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserCredentialService credentialService;

    @MockitoBean
    private TenantService tenantService;

    @MockitoBean
    private RoleService roleService;

    @MockitoBean
    private ResourceService resourceService;

    @MockitoBean
    private SystemSettingService systemSettingService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtTokenFactory jwtTokenFactory;

    @BeforeEach
    void setUpHandlers() {
        SecurityUser principal = new SecurityUser(USER_ID, TENANT_ID, "ops@roseiot.io", true, null, List.of());
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        given(jwtTokenFactory.createAccessToken(any(), any(), any())).willReturn("access-token");
        given(credentialService.checkResetToken(any())).willReturn(UserCredentialService.ResetTokenState.VALID);
        given(userService.getProfile(any()))
                .willReturn(new User(USER_ID, TENANT_ID, "ops@roseiot.io", null, UserStatus.ACTIVE));
    }

    @Test
    void allowsPublicResetTokenValidationWithoutParsingAuthorizationHeader() {
        assertThat(mvc.post()
                        .uri("/api/password-resets/validations")
                        .header("Authorization", "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"recovery-token\"}"))
                .hasStatus2xxSuccessful();
    }

    @Test
    void allowsAnonymousLoginAndPasswordResetRequest() {
        assertThat(mvc.post()
                        .uri("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ops@roseiot.io\",\"password\":\"correct horse\"}"))
                .hasStatus2xxSuccessful();
        assertThat(mvc.post()
                        .uri("/api/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ops@roseiot.io\"}"))
                .hasStatus2xxSuccessful();
    }

    @Test
    void requiresBearerTokenForProfile() {
        assertThat(mvc.get().uri("/api/me")).hasStatus(401);
        verifyNoInteractions(userService);
    }

    @Test
    void acceptsAuthenticatedProfileRequest() {
        SecurityUser principal = new SecurityUser(USER_ID, TENANT_ID, "ops@roseiot.io", true, null, List.of());
        assertThat(mvc.get().uri("/api/me").with(user(principal))).hasStatus(200);
    }

    @Test
    void deniesUnknownAnonymousPathsAndNonPostLoginRequests() {
        assertThat(mvc.get().uri("/api/noauth/not-a-public-route")).hasStatus(401);
        assertThat(mvc.get().uri("/api/login")).hasStatus(403);
    }

    @Test
    void deniesAuthenticatedCallersUnknownRoutesAndNonPostLoginRequests() {
        assertThat(mvc.get().uri("/api/devices").with(user("someone"))).hasStatus(404);
        assertThat(mvc.get().uri("/api/login").with(user("someone"))).hasStatus(403);
        assertThat(mvc.get().uri("/login")).hasStatus(404);
    }
}
