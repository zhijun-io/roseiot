package io.roseiot.tenant.web;

import io.roseiot.iam.infra.security.JwtTokenFactory;
import io.roseiot.iam.domain.ActionCode;
import io.roseiot.iam.infra.security.RoseiotWebMvcTestSupport;
import io.roseiot.iam.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.iam.infra.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.tenant.application.TenantService;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.tenant.persistence.Tenant;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Protocol contract of {@code /api/tenants}: the production filter chain decides authentication,
 * the system context and permission gate runs before the service, and domain failures map to
 * stable error codes. Services are mocked, so this slice never touches the database.
 */
@WebMvcTest(controllers = TenantController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class TenantLifecycleMockMvcTests {

    private static final UUID ADMIN_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000c1");
    private static final UUID TENANT_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000b1");
    private static final long AUTHORIZATION_VERSION = 1L;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TenantService tenantService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    private Tenant tenant(boolean disabled) {
        Tenant tenant = new Tenant();
        tenant.setId(TENANT_ID);
        tenant.setSlug("acme-hvac");
        tenant.setName("Acme HVAC");
        tenant.setDisabledAt(disabled ? Instant.now() : null);
        tenant.setCreatedAt(Instant.parse("2026-10-08T00:00:00Z"));
        return tenant;
    }

    @Test
    void shouldRejectTenantCreationWithoutAuthentication() {
        assertThat(mvc.post().uri("/api/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme HVAC", "slug": "acme-hvac", "initialAdminEmail": "ops@roseiot.io"}
                        """))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("unauthorized");

        verifyNoInteractions(tenantService);
    }

    @Test
    void shouldCreateTenantAndAnswerWithIdNameSlugOnly() {
        given(tenantService.create("Acme HVAC", "acme-hvac", "ops@roseiot.io"))
                .willReturn(tenant(false));

        assertThat(mvc.post().uri("/api/tenants")
                .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme HVAC", "slug": "acme-hvac", "initialAdminEmail": "ops@roseiot.io"}
                        """))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "id": "019f0000-0000-7000-8000-0000000000b1",
                          "name": "Acme HVAC",
                          "slug": "acme-hvac"
                        }
                        """);

        verify(tenantService).create("Acme HVAC", "acme-hvac", "ops@roseiot.io");
    }

    @Test
    void shouldRejectTenantAdminFromCreatingTenantsAtSystemApi() {
        assertThat(mvc.post().uri("/api/tenants")
                .with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme HVAC", "slug": "acme-hvac", "initialAdminEmail": "ops@roseiot.io"}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("forbidden");

        verifyNoInteractions(tenantService);
    }

    @Test
    void shouldRejectCreationWhenTheTenantCreatePermissionIsMissing() {
        assertThat(mvc.post().uri("/api/tenants")
                .with(jwt().jwt(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme HVAC", "slug": "acme-hvac", "initialAdminEmail": "ops@roseiot.io"}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("forbidden");

        verifyNoInteractions(tenantService);
    }

    @Test
    void shouldRejectMalformedCreationBodyBeforeCallingTheService() {
        assertThat(mvc.post().uri("/api/tenants")
                .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme HVAC", "slug": "Acme_HVAC!", "initialAdminEmail": "not-an-email"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("validation_error");

        verifyNoInteractions(tenantService);
    }

    @Test
    void shouldAnswerTakenSlugWithConflictAndNoTokenLeak() {
        willThrow(new BusinessException(ApiErrorCode.TENANT_SLUG_CONFLICT))
                .given(tenantService).create(any(), any(), any());

        assertThat(mvc.post().uri("/api/tenants")
                .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme HVAC", "slug": "acme-hvac", "initialAdminEmail": "ops@roseiot.io"}
                        """))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("tenant_slug_conflict");
    }

    @Test
    void shouldDisableTenantWithTheDisablePermission() {
        given(tenantService.updateTenant(TENANT_ID, null, true)).willReturn(tenant(true));

        assertThat(mvc.patch().uri("/api/tenants/" + TENANT_ID)
                .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"disabled": true}
                        """))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.disabled")
                .isEqualTo(true);

        verify(tenantService).updateTenant(TENANT_ID, null, true);
    }

    @Test
    void shouldRequireTheEnablePermissionToRestoreATenant() {
        assertThat(mvc.patch().uri("/api/tenants/" + TENANT_ID)
                .with(RoseiotWebMvcTestSupport.jwtWithAuthorities(
                        this::systemToken,
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                ActionCode.TENANT_DISABLE)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"disabled": false}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN);

        verifyNoInteractions(tenantService);
    }

    @Test
    void shouldAnswerUnknownTenantWithNotFoundWithoutLeakingExistenceElsewhere() {
        willThrow(new BusinessException(ApiErrorCode.TENANT_NOT_FOUND))
                .given(tenantService).updateTenant(any(), any(), any());

        assertThat(mvc.patch().uri("/api/tenants/" + TENANT_ID)
                .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"disabled": true}
                        """))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("tenant_not_found");
    }

    @Test
    void shouldRejectRenamingWithoutTheLifecyclePermission() {
        assertThat(mvc.patch().uri("/api/tenants/" + TENANT_ID)
                .with(RoseiotWebMvcTestSupport.jwtWithAuthorities(
                        this::systemToken,
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                ActionCode.TENANT_DISABLE),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                ActionCode.TENANT_ENABLE)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme Climate"}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN);

        verifyNoInteractions(tenantService);
    }

    private Jwt.Builder systemToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(ADMIN_ID.toString())
                .claim(JwtTokenFactory.AUTHORIZATION_VERSION, Math.toIntExact(AUTHORIZATION_VERSION))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }

    private Jwt.Builder tenantToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return systemToken(builder).claim("tid", TENANT_ID.toString());
    }
}
