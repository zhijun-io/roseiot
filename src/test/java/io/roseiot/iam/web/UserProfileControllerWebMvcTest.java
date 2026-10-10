package io.roseiot.iam.web;

import io.roseiot.iam.application.UserService;
import io.roseiot.iam.infra.security.RoseiotWebMvcTestSupport;
import io.roseiot.iam.infra.security.JwtTokenFactory;
import io.roseiot.iam.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.iam.infra.security.ImportRoseiotSecuredWebMvc;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(controllers = UserController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class UserControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("019f0000-0000-7000-8000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000b1");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldAnswerProfileWithIdAndEmailOfTheTokenSubject() {
        given(userService.getProfile(USER_ID)).willReturn(new UserService.UserProfile(USER_ID, "ops@roseiot.io", "Ops"));

        assertThat(mvc.get().uri("/me").with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "id": "019f0000-0000-7000-8000-000000000001",
                          "email": "ops@roseiot.io",
                          "name": "Ops"
                        }
                        """);
    }

    @Test
    void shouldAnswerProfileForTenantContextWhenTidMatchesSubject() {
        given(userService.getProfile(USER_ID)).willReturn(new UserService.UserProfile(USER_ID, "tenant@roseiot.io", null));

        assertThat(mvc.get().uri("/me").with(RoseiotWebMvcTestSupport.jwtWithTenantUser(this::tenantToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo("tenant@roseiot.io");
    }

    @Test
    void shouldAnswerProfileForAuthenticatedUserWithoutAnyAuthorities() {
        given(userService.getProfile(USER_ID)).willReturn(new UserService.UserProfile(USER_ID, "plain@roseiot.io", null));

        assertThat(mvc.get().uri("/me").with(RoseiotWebMvcTestSupport.jwtWithTenantUser(this::systemToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo("plain@roseiot.io");
    }

    @Test
    void shouldRejectAnonymousProfileCallers() {
        assertThat(mvc.get().uri("/me"))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(userService);
    }

    @Test
    void shouldUpdateOnlyTheAuthenticatedUsersName() {
        given(userService.updateProfile(USER_ID, "Ada Lovelace"))
                .willReturn(new UserService.UserProfile(USER_ID, "ops@roseiot.io", "Ada Lovelace"));

        assertThat(mvc.put().uri("/me")
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
