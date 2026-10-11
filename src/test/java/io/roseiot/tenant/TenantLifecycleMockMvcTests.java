package io.roseiot.tenant;

import io.roseiot.iam.security.RoseiotWebMvcTestSupport;
import io.roseiot.shared.security.JwtAuthenticationConverter;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Protocol contract of {@code /tenants}: the production filter chain decides authentication,
 * the system context and permission gate runs before the service, and domain failures map to
 * stable error codes. Services are mocked, so this slice never touches the database.
 */
@WebMvcTest(controllers = TenantController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class TenantLifecycleMockMvcTests {

    private static final Long ADMIN_ID = 1001L;
    private static final Long TENANT_ID = 42L;
    private static final String TENANT_CODE = "customer-42";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TenantService tenantService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    private Tenant tenant(boolean disabled) {
        return new Tenant(TENANT_ID, TENANT_CODE, "Acme HVAC",
                disabled ? TenantStatus.DISABLED : TenantStatus.ACTIVE);
    }

    @Test
    void shouldListTenantsForPlatformReader() {
        given(tenantService.list(org.mockito.ArgumentMatchers.any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(new TenantInfo(TENANT_ID, TENANT_CODE, "Acme HVAC", false)),
                        PageRequest.of(0, 50, Sort.by("id")), 1));

        assertThat(mvc.get().uri("/tenants")
                .with(RoseiotWebMvcTestSupport.jwtWithAuthorities(
                        this::systemToken, RoseiotWebMvcTestSupport.action("tenant.read"))))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.content[0].code")
                .isEqualTo(TENANT_CODE);
    }

    @Test
    void shouldGetTenantByIdForPlatformReader() {
        given(tenantService.get(TENANT_ID)).willReturn(tenant(false));

        assertThat(mvc.get().uri("/tenants/" + TENANT_ID)
                .with(RoseiotWebMvcTestSupport.jwtWithAuthorities(
                        this::systemToken, RoseiotWebMvcTestSupport.action("tenant.read"))))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.id")
                .isEqualTo(TENANT_ID.intValue());
    }

    @Test
    void shouldRejectTenantCreationWithoutAuthentication() {
        assertThat(mvc.post().uri("/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme HVAC"}
                        """))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("unauthorized");

        verifyNoInteractions(tenantService);
    }

    @Test
    void shouldCreateTenantAndReturnItsIdAndCode() {
        given(tenantService.create("Acme HVAC"))
                .willReturn(tenant(false));

        assertThat(mvc.post().uri("/tenants")
                .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme HVAC"}
                        """))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "id": 42,
                          "code": "customer-42",
                          "name": "Acme HVAC",
                          "disabled": false
                        }
                        """);

        verify(tenantService).create("Acme HVAC");
    }

    @Test
    void shouldRejectTenantAdminFromCreatingTenantsAtSystemApi() {
        assertThat(mvc.post().uri("/tenants")
                .with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme HVAC"}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("forbidden");

        verifyNoInteractions(tenantService);
    }

    @Test
    void shouldRejectCreationWhenTheTenantCreatePermissionIsMissing() {
        assertThat(mvc.post().uri("/tenants")
                .with(jwt().jwt(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme HVAC"}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("forbidden");

        verifyNoInteractions(tenantService);
    }

    @Test
    void shouldRejectMalformedCreationBodyBeforeCallingTheService() {
        assertThat(mvc.post().uri("/tenants")
                .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": ""}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("validation_error");

        verifyNoInteractions(tenantService);
    }

    @Test
    void shouldDisableTenantWithTheDisablePermission() {
        given(tenantService.update(TENANT_ID, "Acme HVAC", true)).willReturn(tenant(true));

        assertThat(mvc.put().uri("/tenants/" + TENANT_ID)
                .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme HVAC", "disabled": true}
                        """))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.disabled")
                .isEqualTo(true);

        verify(tenantService).update(TENANT_ID, "Acme HVAC", true);
    }

    @Test
    void shouldRequireTheEnablePermissionToRestoreATenant() {
        assertThat(mvc.put().uri("/tenants/" + TENANT_ID)
                .with(RoseiotWebMvcTestSupport.jwtWithAuthorities(
                        this::systemToken,
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "tenant.disable")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme HVAC", "disabled": false}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN);

        verifyNoInteractions(tenantService);
    }

    @Test
    void shouldAnswerUnknownTenantWithNotFoundWithoutLeakingExistenceElsewhere() {
        willThrow(new BusinessException(ApiErrorCode.TENANT_NOT_FOUND))
                .given(tenantService).update(any(), any(), any());

        assertThat(mvc.put().uri("/tenants/" + TENANT_ID)
                .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme HVAC", "disabled": true}
                        """))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("tenant_not_found");
    }

    @Test
    void shouldRejectRenamingWithoutTheLifecyclePermission() {
        assertThat(mvc.put().uri("/tenants/" + TENANT_ID)
                .with(RoseiotWebMvcTestSupport.jwtWithAuthorities(
                        this::systemToken,
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "tenant.disable"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "tenant.enable")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Acme Climate", "disabled": false}
                        """))
                .hasStatus(HttpStatus.FORBIDDEN);

        verifyNoInteractions(tenantService);
    }

    private Jwt.Builder systemToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(ADMIN_ID.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }

    private Jwt.Builder tenantToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return systemToken(builder).claim("tid", TENANT_ID.toString());
    }
}
