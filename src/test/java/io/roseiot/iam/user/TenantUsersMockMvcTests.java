package io.roseiot.iam.user;

import io.roseiot.iam.security.RoseiotWebMvcTestSupport;
import io.roseiot.iam.security.JwtAuthenticationConverter;
import io.roseiot.iam.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.shared.ApiErrorCode;
import io.roseiot.shared.BusinessException;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@WebMvcTest(controllers = UserController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class TenantUsersMockMvcTests {
    private static final Long TENANT_ID = 42L;
    private static final Long MANAGED_USER_ID = 1004L;
    private static final Long ADMIN_USER_ID = 1005L;
    private static final Long ROLE_ID = 1006L;

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
    void shouldListMembers() {
        given(userService.listTenantUsers(TENANT_ID))
                .willReturn(List.of(new UserInfo(
                        MANAGED_USER_ID, "member@roseiot.io", "Member", ROLE_ID, false)));

        assertThat(mvc.get().uri("/users").with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        [
                          {
                            "id": 1004,
                            "email": "member@roseiot.io",
                            "name": "Member",
                            "roleId": 1006,
                            "disabled": false
                          }
                        ]
                        """);
    }

    @Test
    void shouldRejectPlatformJwtWithoutMemberManagement() {
        assertThat(mvc.get().uri("/users").with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldUpdateMemberRole() {
        assertThat(mvc.put().uri("/users/" + MANAGED_USER_ID)
                        .with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disabled": false, "roleId": 1006}
                                """))
                .hasStatus(HttpStatus.OK);

        verify(userService).updateTenantUser(TENANT_ID, MANAGED_USER_ID, false, ROLE_ID);
    }

    @Test
    void shouldUpdateMemberDisabledState() {
        assertThat(mvc.put().uri("/users/" + MANAGED_USER_ID)
                        .with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disabled": true, "roleId": 1006}
                                """))
                .hasStatus(HttpStatus.OK);
        verify(userService).updateTenantUser(TENANT_ID, MANAGED_USER_ID, true, ROLE_ID);
    }

    @Test
    void shouldRejectPatchWithoutAnyChange() {
        assertThat(mvc.put().uri("/users/" + MANAGED_USER_ID)
                        .with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("validation_error");
    }

    @Test
    void shouldMapLastTenantAdminConflict() {
        willThrow(new BusinessException(ApiErrorCode.LAST_TENANT_ADMIN))
                .given(userService)
                .updateTenantUser(eq(TENANT_ID), eq(MANAGED_USER_ID), eq(true), eq(ROLE_ID));

        assertThat(mvc.put().uri("/users/" + MANAGED_USER_ID)
                        .with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disabled": true, "roleId": 1006}
                                """))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("last_tenant_admin");
    }

    private Jwt.Builder tenantToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(ADMIN_USER_ID.toString())
                .claim("sid", Long.toString(System.nanoTime()))
                .claim("tid", TENANT_ID.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }

    private Jwt.Builder systemToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(ADMIN_USER_ID.toString())
                .claim("sid", Long.toString(System.nanoTime()))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}
