package io.roseiot.tenant.application;

import io.roseiot.iam.domain.ActionCode;
import io.roseiot.TestcontainersConfig;
import io.roseiot.iam.application.RoleService;
import io.roseiot.iam.application.UserSessionService;
import io.roseiot.iam.infra.security.SecurityUser;
import io.roseiot.iam.infra.security.SecurityUserDetailsService;
import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.tenant.infra.invitation.TenantInvitationNotifier;
import io.roseiot.tenant.infra.mybatis.TenantContext;
import io.roseiot.tenant.persistence.Tenant;
import io.roseiot.tenant.persistence.TenantInvitationMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

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
    private SecurityUserDetailsService securityUserDetailsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantInvitationMapper invitationMapper;

    @MockitoBean
    private TenantInvitationNotifier tenantInvitationNotifier;

    private UUID roleIdOf(Tenant tenant, String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM \"role\" WHERE tenant_id = ? AND code = ?", UUID.class, tenant.getId(), code);
    }

    private UUID userIdByEmail(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    }

    @Test
    void shouldListUsersAndProtectLastTenantAdmin() {
        Tenant tenant = tenantService.create("Acme", "acme-users", "admin@roseiot.io");
        ArgumentCaptor<String> adminToken = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier)
                .sendInvitation(eq("admin@roseiot.io"), eq("Administrator"), adminToken.capture());
        invitationService.acceptInvitation(adminToken.getValue(), "newSecurePass9012");

        TenantContext.call(tenant.getId(), () ->
                invitationService.create(tenant.getId(), "member@roseiot.io", roleIdOf(tenant, RoleService.DEVICE_OPERATOR)));
        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier, times(2)).sendInvitation(any(), any(), tokens.capture());
        invitationService.acceptInvitation(tokens.getAllValues().get(1), "newSecurePass9012");

        UUID adminId = userIdByEmail("admin@roseiot.io");
        TenantContext.call(tenant.getId(), () -> {
            assertThat(userManagementService.listUsers(tenant.getId())).hasSize(2);
            assertThatThrownBy(() -> userManagementService.updateUser(tenant.getId(), adminId, true, null))
                    .isInstanceOf(BusinessException.class);
            return null;
        });
    }

    @Test
    void shouldRejectLoginAfterTenantDisabled() {
        Tenant tenant = tenantService.create("Beta", "beta-off", "owner@roseiot.io");
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier)
                .sendInvitation(eq("owner@roseiot.io"), eq("Administrator"), token.capture());
        invitationService.acceptInvitation(token.getValue(), "newSecurePass9012");

        tenantService.updateTenant(tenant.getId(), null, true);

        assertThatThrownBy(() -> securityUserDetailsService.loadUserByUsername("owner@roseiot.io"))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    }

    @Test
    void shouldRejectCrossTenantUserAndInvitationAccess() {
        Tenant firstTenant = tenantService.create("First", "first-isolation", "first@roseiot.io");
        Tenant secondTenant = tenantService.create("Second", "second-isolation", "second@roseiot.io");
        TenantInvitationService.InvitationSummary otherTenantInvitation =
                TenantContext.call(secondTenant.getId(), () -> invitationService.list(secondTenant.getId(), "pending").getFirst());
        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier, times(2)).sendInvitation(any(), any(), tokens.capture());
        invitationService.acceptInvitation(tokens.getAllValues().get(0), "newSecurePass9012");
        invitationService.acceptInvitation(tokens.getAllValues().get(1), "newSecurePass9012");
        UUID otherTenantUserId = userIdByEmail("second@roseiot.io");

        TenantContext.call(firstTenant.getId(), () -> {
            assertThatThrownBy(() -> userManagementService.updateUser(firstTenant.getId(), otherTenantUserId, true, null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.errorCode())
                                    .isEqualTo(ApiErrorCode.TENANT_USER_NOT_FOUND));
            assertThatThrownBy(() -> invitationService.get(firstTenant.getId(), otherTenantInvitation.id()))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> assertThat(exception.errorCode())
                                    .isEqualTo(ApiErrorCode.INVITATION_NOT_FOUND));
            return null;
        });

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status = 'ACTIVE' FROM users WHERE id = ?", Boolean.class, otherTenantUserId))
                .isTrue();
    }

    @Test
    void shouldKeepSameCodeRolesSeparatePerTenant() {
        Tenant firstTenant = tenantService.create("Scope A", "scope-a", "scope-a@roseiot.io");
        Tenant secondTenant = tenantService.create("Scope B", "scope-b", "scope-b@roseiot.io");
        UUID adminRoleOfFirst = roleIdOf(firstTenant, RoleService.TENANT_ADMIN);
        UUID adminRoleOfSecond = roleIdOf(secondTenant, RoleService.TENANT_ADMIN);
        assertThat(adminRoleOfFirst).isNotEqualTo(adminRoleOfSecond);

        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(tenantInvitationNotifier, times(2)).sendInvitation(any(), any(), tokens.capture());
        invitationService.acceptInvitation(tokens.getAllValues().get(0), "newSecurePass9012");
        invitationService.acceptInvitation(tokens.getAllValues().get(1), "newSecurePass9012");
        UUID firstUserId = userIdByEmail("scope-a@roseiot.io");

        SecurityUser firstUser = securityUserDetailsService.loadByUserId(firstUserId);
        assertThat(firstUser.tenantId()).isEqualTo(firstTenant.getId());
        assertThat(firstUser.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder(ActionCode.TENANT_USER_MANAGE, ActionCode.TENANT_USER_INVITE, ActionCode.TENANT_ROLE_MANAGE);

        // The composite (tenant_id, role_id) key makes a cross-tenant assignment impossible.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_role (id, tenant_id, user_id, role_id) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), firstTenant.getId(), firstUserId, adminRoleOfSecond))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldApplyMybatisTenantPluginToInvitationQueries() {
        Tenant firstTenant = tenantService.create("Plugin First", "plugin-first", "plugin-first@roseiot.io");
        Tenant secondTenant = tenantService.create("Plugin Second", "plugin-second", "plugin-second@roseiot.io");
        TenantInvitationService.InvitationSummary firstInvitation =
                TenantContext.call(firstTenant.getId(), () -> invitationService.list(firstTenant.getId(), "pending").getFirst());
        TenantInvitationService.InvitationSummary secondInvitation =
                TenantContext.call(secondTenant.getId(), () -> invitationService.list(secondTenant.getId(), "pending").getFirst());

        TenantContext.call(firstTenant.getId(), () -> {
            assertThat(invitationMapper.selectById(firstInvitation.id())).isNotNull();
            assertThat(invitationMapper.selectById(secondInvitation.id())).isNull();
            return null;
        });
    }
}
