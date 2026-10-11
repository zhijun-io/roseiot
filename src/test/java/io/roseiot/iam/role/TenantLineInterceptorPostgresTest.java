package io.roseiot.iam.role;

import io.roseiot.TestcontainersConfig;
import io.roseiot.shared.mybatis.TenantContext;
import io.roseiot.tenant.Tenant;
import io.roseiot.tenant.TenantService;
import io.roseiot.iam.user.UserService;
import io.roseiot.iam.user.UserInfo;
import io.roseiot.shared.security.SecurityUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
@WithMockUser(authorities = {
        "tenant.create", "tenant.disable", "tenant.enable", "tenant.read",
        "tenant.role.manage", "tenant.user.manage"
})
class TenantLineInterceptorPostgresTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(authorities = "tenant.user.manage")
    void rejectsRoleServiceCallWithoutRoleManagementAuthority() {
        assertThatThrownBy(() -> roleService.list(1L, PageRequest.of(0, 10, Sort.by("id"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void preventsServiceFromReadingAnotherUsersProfile() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> userService.getProfile(1L))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        SecurityUser principal = new SecurityUser(
                1L, 1L, "current@roseiot.test", true, "", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        try {
            assertThatThrownBy(() -> userService.getProfile(2L))
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void scopesMapperQueriesToCurrentTenant() {
        Tenant firstTenant = tenantService.create("First tenant");
        Tenant secondTenant = tenantService.create("Second tenant");
        long firstRoleId = insertRole(firstTenant.id(), "First role");
        long secondRoleId = insertRole(secondTenant.id(), "Second role");

        List<RoleEntity> visibleRoles = TenantContext.call(firstTenant.id(), () -> roleMapper.selectList(null));

        assertThat(visibleRoles).extracting(RoleEntity::getId).containsExactly(firstRoleId);
        assertThat(secondRoleId).isNotEqualTo(firstRoleId);
    }

    @Test
    void rejectsMapperQueriesWithoutTenantContext() {
        assertThatThrownBy(() -> roleMapper.selectList(null))
                .hasRootCauseInstanceOf(AccessDeniedException.class)
                .hasRootCauseMessage("Tenant context required");
    }

    @Test
    void preventsCrossTenantMapperUpdatesAndDeletes() {
        Tenant firstTenant = tenantService.create("First tenant");
        Tenant secondTenant = tenantService.create("Second tenant");
        long secondRoleId = insertRole(secondTenant.id(), "Second role");

        TenantContext.call(firstTenant.id(), () -> {
            RoleEntity attemptedUpdate = new RoleEntity();
            attemptedUpdate.setId(secondRoleId);
            attemptedUpdate.setTenantId(secondTenant.id());
            attemptedUpdate.setName("Changed by another tenant");

            assertThat(roleMapper.updateById(attemptedUpdate)).isZero();
            assertThat(roleMapper.deleteById(secondRoleId)).isZero();
            return null;
        });

        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM \"role\" WHERE id = ?", String.class, secondRoleId))
                .isEqualTo("Second role");
    }

    @Test
    void createsRoleWithTenantFromScopedServiceOperation() {
        Tenant tenant = tenantService.create("Scoped tenant");

        RoleInfo role = TenantContext.call(tenant.id(),
                () -> roleService.create(tenant.id(), "Scoped role", java.util.Set.of()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM \"role\" WHERE id = ?", Long.class, role.id()))
                .isEqualTo(tenant.id());
    }

    @Test
    void paginatesRoleAndUserResponsesAgainstPostgres() {
        Tenant tenant = tenantService.create("Paged tenant");
        TenantContext.call(tenant.id(), () -> {
            RoleInfo role = roleService.create(tenant.id(), "Member manager", java.util.Set.of("tenant.user.manage"));
            for (int index = 0; index < 3; index++) {
                userService.create(tenant.id(), "member" + index + "@roseiot.test", "Member " + index,
                        "StrongPass123!", role.id());
            }
            return null;
        });

        Page<RoleInfo> firstRolePage = TenantContext.call(tenant.id(),
                () -> roleService.list(tenant.id(), PageRequest.of(0, 2, Sort.by("id"))));
        Page<UserInfo> firstUserPage = TenantContext.call(tenant.id(),
                () -> userService.list(tenant.id(), PageRequest.of(0, 2, Sort.by("id"))));
        Page<UserInfo> secondUserPage = TenantContext.call(tenant.id(),
                () -> userService.list(tenant.id(), PageRequest.of(1, 2, Sort.by("id"))));

        assertThat(firstRolePage.getTotalElements()).isEqualTo(1);
        assertThat(firstRolePage.getContent()).extracting(RoleInfo::name).containsExactly("Member manager");
        assertThat(firstUserPage.getTotalElements()).isEqualTo(3);
        assertThat(firstUserPage.getContent()).hasSize(2);
        assertThat(secondUserPage.getContent()).hasSize(1);
        assertThat(firstUserPage.getContent()).doesNotContainAnyElementsOf(secondUserPage.getContent());
    }

    private long insertRole(Long tenantId, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO \"role\" (tenant_id, name) VALUES (?, ?) RETURNING id",
                Long.class,
                tenantId,
                name);
    }
}
