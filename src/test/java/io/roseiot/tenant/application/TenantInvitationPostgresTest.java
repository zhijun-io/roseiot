package io.roseiot.tenant.application;

import io.roseiot.TestcontainersConfig;
import io.roseiot.identity.application.UserSessionService;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.tenant.infra.invitation.TenantInvitationNotifier;
import io.roseiot.tenant.persistence.Tenant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@Transactional
class TenantInvitationPostgresTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantInvitationService invitationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TenantInvitationNotifier tenantInvitationNotifier;

    @Test
    void shouldAcceptInitialTenantAdminInvitationAndCreateUser() {
        Tenant tenant = tenantService.create("Acme HVAC", "acme-accept", "admin@roseiot.io");

        ArgumentCaptor<String> plainToken = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier).sendInvitation(eq("admin@roseiot.io"), eq("TenantAdmin"), plainToken.capture());

        UserSessionService.SessionTokens tokens =
                invitationService.acceptInvitation(plainToken.getValue(), "Passw0rd9012");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();

        Map<String, Object> userRow = jdbcTemplate.queryForMap(
                "SELECT tenant_id, email FROM users WHERE email = ?", "admin@roseiot.io");
        assertThat(userRow)
                .containsEntry("tenant_id", tenant.getId())
                .containsEntry("email", "admin@roseiot.io");

        UUID userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", UUID.class, "admin@roseiot.io");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM user_role WHERE user_id = ? AND scope_tenant_id = ?",
                        Integer.class,
                        userId,
                        tenant.getId()))
                .isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT accepted_at IS NOT NULL FROM tenant_user_invitation WHERE tenant_id = ?",
                        Boolean.class,
                        tenant.getId()))
                .isTrue();
    }

    @Test
    void shouldCreateAndRevokePendingInvitation() {
        Tenant tenant = tenantService.create("Beta", "beta-inv", "owner@roseiot.io");

        ArgumentCaptor<String> ownerToken = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier).sendInvitation(eq("owner@roseiot.io"), eq("TenantAdmin"), ownerToken.capture());
        invitationService.acceptInvitation(ownerToken.getValue(), "Passw0rd9012");

        TenantInvitationService.InvitationSummary created =
                invitationService.create(tenant.getId(), "member@roseiot.io", "TenantUser");

        invitationService.revoke(tenant.getId(), created.id());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT revoked_at IS NOT NULL FROM tenant_user_invitation WHERE id = ?",
                        Boolean.class,
                        created.id()))
                .isTrue();
    }
}
