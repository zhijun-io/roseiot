package io.roseiot.tenant.web;

import io.roseiot.identity.infra.security.RoseiotWebMvcTestSupport;
import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.identity.infra.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.tenant.application.TenantUserService;
import io.roseiot.tenant.application.TenantUserService.TenantUserSummary;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(controllers = TenantUserController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class TenantUsersMockMvcTests {

    private static final UUID TENANT_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000b1");
    private static final UUID MANAGED_USER_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000e1");
    private static final UUID ADMIN_USER_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000d1");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TenantUserService userManagementService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldListTenantUsers() {
        given(userManagementService.listUsers(TENANT_ID))
                .willReturn(List.of(new TenantUserSummary(
                        MANAGED_USER_ID, "member@roseiot.io", "TenantUser", false)));

        assertThat(mvc.get().uri("/api/tenants/users").with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$[0].email")
                .isEqualTo("member@roseiot.io");
    }

    @Test
    void shouldRejectSystemJwtOnTenantUsers() {
        assertThat(mvc.get().uri("/api/tenants/users").with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldPatchTenantUser() {
        assertThat(mvc.patch().uri("/api/tenants/users/" + MANAGED_USER_ID)
                        .with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disabled": true}
                                """))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(userManagementService).updateUser(TENANT_ID, MANAGED_USER_ID, true, null);
    }

    @Test
    void shouldMapLastTenantAdminConflict() {
        willThrow(new BusinessException(ApiErrorCode.LAST_TENANT_ADMIN))
                .given(userManagementService)
                .updateUser(eq(TENANT_ID), eq(MANAGED_USER_ID), eq(true), any());

        assertThat(mvc.patch().uri("/api/tenants/users/" + MANAGED_USER_ID)
                        .with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"disabled": true}
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
                .claim("sid", UUID.randomUUID().toString())
                .claim("tid", TENANT_ID.toString())
                .claim(IdentityJwtClaims.AUTHORIZATION_VERSION, 1)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }

    private Jwt.Builder systemToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(ADMIN_USER_ID.toString())
                .claim(IdentityJwtClaims.AUTHORIZATION_VERSION, 1)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}
