package io.roseiot.tenant.application;

import io.roseiot.TestcontainersConfig;
import io.roseiot.identity.application.UserSessionService;
import io.roseiot.identity.application.RoleService;
import io.roseiot.identity.infra.security.SecurityUser;
import io.roseiot.identity.infra.security.SecurityUserDetailsService;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantUserInvitationMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import io.roseiot.tenant.infra.invitation.TenantInvitationNotifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
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
    private SecurityUserDetailsService securityUserDetailsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantUserInvitationMapper invitationMapper;

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

        SecurityUser owner =
                (SecurityUser) securityUserDetailsService.loadUserByUsername("owner@roseiot.io");
        assertThatThrownBy(() -> userSessionService.openSession(owner))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }

    @Test
    void shouldRejectCrossTenantUserAndInvitationAccess() {
        Tenant firstTenant = tenantService.create("First", "first-isolation", "first@roseiot.io");
        Tenant secondTenant = tenantService.create("Second", "second-isolation", "second@roseiot.io");
        TenantInvitationService.InvitationSummary otherTenantInvitation =
                invitationService.list(secondTenant.getId(), "pending").getFirst();
        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier, times(2)).sendInvitation(any(), any(), tokens.capture());
        invitationService.acceptInvitation(tokens.getAllValues().get(0), "newSecurePass9012");
        invitationService.acceptInvitation(tokens.getAllValues().get(1), "newSecurePass9012");
        UUID otherTenantUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", UUID.class, "second@roseiot.io");

        assertThatThrownBy(() -> userManagementService.updateUser(firstTenant.getId(), otherTenantUserId, true, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(io.roseiot.shared.domain.ApiErrorCode.TENANT_USER_NOT_FOUND));
        assertThatThrownBy(() -> invitationService.get(firstTenant.getId(), otherTenantInvitation.id()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(io.roseiot.shared.domain.ApiErrorCode.INVITATION_NOT_FOUND));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT disabled_at IS NULL FROM users WHERE id = ?", Boolean.class, otherTenantUserId))
                .isTrue();
    }

    @Test
    void shouldIgnoreRoleAssignmentsOutsideUsersTenantScope() {
        Tenant tenant = tenantService.create("Scoped", "scope-isolation", "owner-scope@roseiot.io");
        invitationService.create(tenant.getId(), "member-scope@roseiot.io", RoleService.TENANT_USER);
        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier, times(2)).sendInvitation(any(), any(), tokens.capture());
        invitationService.acceptInvitation(tokens.getAllValues().get(1), "newSecurePass9012");
        UUID userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", UUID.class, "member-scope@roseiot.io");
        UUID adminRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM \"role\" WHERE code = ?", UUID.class, RoleService.TENANT_ADMIN);
        UUID anotherTenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO user_role (id, user_id, role_id, scope_tenant_id) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), userId, adminRoleId, anotherTenantId);

        assertThat(securityUserDetailsService.loadByUserId(userId).getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains("ROLE_" + RoleService.TENANT_USER)
                .doesNotContain("ROLE_" + RoleService.TENANT_ADMIN, "tenant.user.manage");
        assertThat(userManagementService.listUsers(tenant.getId()))
                .extracting(TenantUserService.TenantUserSummary::id, TenantUserService.TenantUserSummary::role)
                .contains(tuple(userId, RoleService.TENANT_USER));
    }

    @Test
    void shouldApplyMybatisTenantPluginToInvitationQueries() {
        Tenant firstTenant = tenantService.create("Plugin First", "plugin-first", "plugin-first@roseiot.io");
        Tenant secondTenant = tenantService.create("Plugin Second", "plugin-second", "plugin-second@roseiot.io");
        TenantInvitationService.InvitationSummary firstInvitation =
                invitationService.list(firstTenant.getId(), "pending").getFirst();
        TenantInvitationService.InvitationSummary secondInvitation =
                invitationService.list(secondTenant.getId(), "pending").getFirst();
        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier, times(2)).sendInvitation(any(), any(), tokens.capture());
        invitationService.acceptInvitation(tokens.getAllValues().get(0), "newSecurePass9012");
        invitationService.acceptInvitation(tokens.getAllValues().get(1), "newSecurePass9012");
        UUID firstUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", UUID.class, "plugin-first@roseiot.io");
        SecurityUser firstUser = securityUserDetailsService.loadByUserId(firstUserId);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                firstUser, null, firstUser.getAuthorities()));

        try {
            assertThat(invitationMapper.selectById(firstInvitation.id())).isNotNull();
            assertThat(invitationMapper.selectById(secondInvitation.id())).isNull();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
