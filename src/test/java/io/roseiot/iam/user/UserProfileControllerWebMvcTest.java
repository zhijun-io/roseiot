package io.roseiot.iam.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.roseiot.iam.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.iam.security.RoseiotWebMvcTestSupport;
import io.roseiot.shared.security.JwtAuthenticationConverter;
import io.roseiot.tenant.TenantService;
import java.time.Instant;
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

@WebMvcTest(controllers = UserController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class UserControllerWebMvcTest {
    @MockitoBean
    private TenantService tenantService;

    private static final Long USER_ID = 1007L;
    private static final Long TENANT_ID = 42L;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserCredentialService userCredentialService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldAnswerProfileWithIdAndEmailOfTheTokenSubject() {
        given(userService.getProfile(USER_ID)).willReturn(new UserProfile(USER_ID, "ops@roseiot.io", "Ops"));

        assertThat(mvc.get().uri("/api/me").with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "id": 1007,
                          "email": "ops@roseiot.io",
                          "name": "Ops"
                        }
                        """);
    }

    @Test
    void shouldAnswerProfileForTenantContextWhenTidMatchesSubject() {
        given(userService.getProfile(USER_ID)).willReturn(new UserProfile(USER_ID, "tenant@roseiot.io", null));

        assertThat(mvc.get().uri("/api/me").with(RoseiotWebMvcTestSupport.jwtWithTenantUser(this::tenantToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo("tenant@roseiot.io");
    }

    @Test
    void shouldAnswerProfileForAuthenticatedUserWithoutAnyAuthorities() {
        given(userService.getProfile(USER_ID)).willReturn(new UserProfile(USER_ID, "plain@roseiot.io", null));

        assertThat(mvc.get().uri("/api/me").with(RoseiotWebMvcTestSupport.jwtWithTenantUser(this::systemToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo("plain@roseiot.io");
    }

    @Test
    void shouldRejectAnonymousProfileCallers() {
        assertThat(mvc.get().uri("/api/me")).hasStatus(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(userService);
    }

    @Test
    void shouldUpdateOnlyTheAuthenticatedUsersName() {
        given(userService.updateProfile(USER_ID, "Ada Lovelace"))
                .willReturn(new UserProfile(USER_ID, "ops@roseiot.io", "Ada Lovelace"));

        assertThat(mvc.put()
                        .uri("/api/me")
                        .with(RoseiotWebMvcTestSupport.jwtWithTenantUser(this::tenantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada Lovelace\"}"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("Ada Lovelace");

        verify(userService).updateProfile(USER_ID, "Ada Lovelace");
    }

    private Jwt.Builder systemToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(USER_ID.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }

    private Jwt.Builder tenantToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(USER_ID.toString())
                .claim("tid", TENANT_ID.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}
