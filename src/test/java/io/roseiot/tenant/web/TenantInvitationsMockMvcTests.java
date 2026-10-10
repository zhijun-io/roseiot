package io.roseiot.tenant.web;

import io.roseiot.iam.infra.security.RoseiotWebMvcTestSupport;
import io.roseiot.iam.infra.security.JwtTokenFactory;
import io.roseiot.iam.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.iam.infra.security.ImportRoseiotSecuredWebMvc;
import io.roseiot.tenant.application.TenantInvitationService;
import io.roseiot.tenant.application.TenantInvitationService.InvitationSummary;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@WebMvcTest(controllers = TenantInvitationController.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@ImportRoseiotSecuredWebMvc
class TenantInvitationsMockMvcTests {

    private static final UUID TENANT_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000b1");
    private static final UUID INVITATION_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000c1");
    private static final UUID USER_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000d1");
    private static final UUID ROLE_ID = UUID.fromString("019f0000-0000-7000-8000-0000000000a2");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TenantInvitationService invitationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void shouldCreateInvitationWithTenantJwt() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        given(invitationService.create(eq(TENANT_ID), eq("new@roseiot.io"), eq(ROLE_ID)))
                .willReturn(new InvitationSummary(
                        INVITATION_ID, "new@roseiot.io", ROLE_ID, now.plusSeconds(3600), now, null, null));

        assertThat(mvc.post().uri("/tenants/" + TENANT_ID + "/invitations")
                        .with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "new@roseiot.io", "roleId": "019f0000-0000-7000-8000-0000000000a2"}
                                """))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "id": "019f0000-0000-7000-8000-0000000000c1",
                          "email": "new@roseiot.io",
                          "roleId": "019f0000-0000-7000-8000-0000000000a2",
                          "expiresAt": "2026-10-09T01:00:00Z",
                          "createdAt": "2026-10-09T00:00:00Z",
                          "acceptedAt": null,
                          "revokedAt": null
                        }
                        """);
    }

    @Test
    void shouldRejectPlatformJwtForInvitations() {
        assertThat(mvc.post().uri("/tenants/" + TENANT_ID + "/invitations")
                        .with(RoseiotWebMvcTestSupport.jwtWithSystemAdmin(this::systemToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "new@roseiot.io", "roleId": "019f0000-0000-7000-8000-0000000000a2"}
                                """))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldListInvitations() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        given(invitationService.list(TENANT_ID, null))
                .willReturn(List.of(new InvitationSummary(
                        INVITATION_ID, "new@roseiot.io", ROLE_ID, now.plusSeconds(3600), now, null, null)));

        assertThat(mvc.get().uri("/tenants/" + TENANT_ID + "/invitations")
                        .with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken)))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$[0].email")
                .isEqualTo("new@roseiot.io");
    }

    @Test
    void shouldRevokeInvitation() {
        assertThat(mvc.delete().uri("/tenants/" + TENANT_ID + "/invitations/" + INVITATION_ID)
                        .with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken)))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(invitationService).revoke(TENANT_ID, INVITATION_ID);
    }

    @Test
    void shouldMapMissingInvitationToNotFound() {
        willThrow(new BusinessException(ApiErrorCode.INVITATION_NOT_FOUND))
                .given(invitationService)
                .revoke(TENANT_ID, INVITATION_ID);

        assertThat(mvc.delete().uri("/tenants/" + TENANT_ID + "/invitations/" + INVITATION_ID)
                        .with(RoseiotWebMvcTestSupport.jwtWithTenantAdmin(this::tenantToken)))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo("invitation_not_found");
    }

    private Jwt.Builder tenantToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(USER_ID.toString())
                .claim("sid", UUID.randomUUID().toString())
                .claim("tid", TENANT_ID.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }

    private Jwt.Builder systemToken(Jwt.Builder builder) {
        Instant now = Instant.now();
        return builder.header("alg", "none")
                .subject(USER_ID.toString())
                .claim("sid", UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60));
    }
}
