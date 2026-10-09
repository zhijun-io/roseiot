package io.roseiot.tenant.application;

import io.roseiot.TestcontainersConfig;
import io.roseiot.identity.application.UserSessionService;
import io.roseiot.shared.domain.BusinessException;
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
import io.roseiot.tenant.infra.invitation.TenantInvitationNotifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "roseiot.rate-limit.enabled=false")
@Transactional
class TenantUserManagementPostgresTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantInvitationService invitationService;

    @Autowired
    private TenantUserService userManagementService;

    @Autowired
    private UserSessionService userSessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TenantInvitationNotifier tenantInvitationNotifier;

    @Test
    void shouldListUsersAndProtectLastTenantAdmin() {
        Tenant tenant = tenantService.create("Acme", "acme-users", "admin@roseiot.io");
        ArgumentCaptor<String> adminToken = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier).sendInvitation(eq("admin@roseiot.io"), eq("TenantAdmin"), adminToken.capture());
        invitationService.acceptInvitation(adminToken.getValue(), "newSecurePass9012");

        invitationService.create(tenant.getId(), "member@roseiot.io", "TenantUser");
        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier, times(2)).sendInvitation(any(), any(), tokens.capture());
        invitationService.acceptInvitation(tokens.getAllValues().get(1), "newSecurePass9012");

        assertThat(userManagementService.listUsers(tenant.getId())).hasSize(2);

        UUID adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", UUID.class, "admin@roseiot.io");

        assertThatThrownBy(() -> userManagementService.updateUser(tenant.getId(), adminId, true, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldRejectLoginAfterTenantDisabled() {
        Tenant tenant = tenantService.create("Beta", "beta-off", "owner@roseiot.io");
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier).sendInvitation(eq("owner@roseiot.io"), eq("TenantAdmin"), token.capture());
        invitationService.acceptInvitation(token.getValue(), "newSecurePass9012");

        tenantService.updateTenant(tenant.getId(), null, true);

        assertThatThrownBy(() -> userSessionService.login("owner@roseiot.io", "newSecurePass9012"))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }
}
