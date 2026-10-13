package io.roseiot.iam.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.roseiot.TestcontainersConfig;
import io.roseiot.iam.user.UserResponse;
import io.roseiot.iam.user.UserService;
import io.roseiot.shared.mybatis.TenantContext;
import io.roseiot.shared.security.SecurityUser;
import io.roseiot.tenant.Tenant;
import io.roseiot.tenant.TenantService;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
@WithMockUser(
        authorities = {
            "tenant.create",
            "tenant.disable",
            "tenant.enable",
            "tenant.read",
            "tenant.role.manage",
            "tenant.user.manage"
        })
class TenantLineInterceptorIT {

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

        SecurityUser principal = new SecurityUser(1L, 1L, "current@roseiot.test", true, "", List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

        try {
            assertThatThrownBy(() -> userService.getProfile(2L)).isInstanceOf(AccessDeniedException.class);
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

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM \"role\" WHERE id = ?", String.class, secondRoleId))
                .isEqualTo("Second role");
    }

    @Test
    void scopesCustomAssignmentQueryToBothSqlTenantAndAuthenticatedTenant() {
        Tenant firstTenant = tenantService.create("First assignment tenant");
        Tenant secondTenant = tenantService.create("Second assignment tenant");
        long secondRoleId = insertRole(secondTenant.id(), "Assigned role");
        long secondUserId = insertUser(secondTenant.id(), "assignment@example.test");
        jdbcTemplate.update(
                "INSERT INTO user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)",
                secondTenant.id(),
                secondUserId,
                secondRoleId);

        assertThat(TenantContext.call(
                        firstTenant.id(), () -> roleMapper.hasAssignments(secondTenant.id(), secondRoleId)))
                .isZero();
        assertThat(TenantContext.call(
                        secondTenant.id(), () -> roleMapper.hasAssignments(secondTenant.id(), secondRoleId)))
                .isEqualTo(1);
    }

    @Test
    void preventsAssigningMultipleRolesToOneTenantUser() {
        Tenant tenant = tenantService.create("Single role tenant");
        long firstRoleId = insertRole(tenant.id(), "First role");
        long secondRoleId = insertRole(tenant.id(), "Second role");
        long userId = insertUser(tenant.id(), "single-role@example.test");
        jdbcTemplate.update(
                "INSERT INTO user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)",
                tenant.id(),
                userId,
                firstRoleId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)",
                        tenant.id(),
                        userId,
                        secondRoleId))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void loadsOnlyPermissionsFromTheUsersTenant() {
        Tenant firstTenant = tenantService.create("First permission tenant");
        Tenant secondTenant = tenantService.create("Second permission tenant");
        Role firstRole = TenantContext.call(
                firstTenant.id(),
                () -> roleService.create(
                        firstTenant.id(), "First role", java.util.Set.of("device.read", "tenant.user.manage")));
        Role secondRole = TenantContext.call(
                secondTenant.id(),
                () -> roleService.create(secondTenant.id(), "Second role", java.util.Set.of("device.command.issue")));
        UserResponse firstUser = TenantContext.call(
                firstTenant.id(),
                () -> userService.create(
                        firstTenant.id(),
                        "first-permission@example.test",
                        "First user",
                        "StrongPass123!",
                        firstRole.id()));

        SecurityUser loaded = userService.loadByUserId(firstUser.id());

        assertThat(loaded.getAuthorities())
                .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("device.read", "tenant.user.manage")
                .doesNotContain("device.command.issue");
    }

    @Test
    void rejectsCrossTenantRoleAssignmentsAtTheDatabaseBoundary() {
        Tenant firstTenant = tenantService.create("First constrained tenant");
        Tenant secondTenant = tenantService.create("Second constrained tenant");
        long secondRoleId = insertRole(secondTenant.id(), "Second tenant role");
        long firstUserId = insertUser(firstTenant.id(), "first-constrained@example.test");

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)",
                        secondTenant.id(),
                        firstUserId,
                        secondRoleId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void fillsUserAuditTimestampsOnInsertAndEntityUpdate() {
        Tenant tenant = tenantService.create("Audit metadata tenant");
        Role role = TenantContext.call(
                tenant.id(),
                () -> roleService.create(tenant.id(), "Member manager", java.util.Set.of("tenant.user.manage")));
        UserResponse user = TenantContext.call(
                tenant.id(),
                () -> userService.create(
                        tenant.id(), "audit-user@example.test", "Audit user", "StrongPass123!", role.id()));
        Timestamp createdAt =
                jdbcTemplate.queryForObject("SELECT created_at FROM \"user\" WHERE id = ?", Timestamp.class, user.id());
        Timestamp originalUpdatedAt =
                jdbcTemplate.queryForObject("SELECT updated_at FROM \"user\" WHERE id = ?", Timestamp.class, user.id());

        SecurityUser principal = new SecurityUser(user.id(), tenant.id(), user.email(), true, null, List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        try {
            userService.updateProfile(user.id(), "Updated audit user");
        } finally {
            SecurityContextHolder.clearContext();
        }

        Timestamp updatedAt =
                jdbcTemplate.queryForObject("SELECT updated_at FROM \"user\" WHERE id = ?", Timestamp.class, user.id());
        assertThat(createdAt).isNotNull();
        assertThat(originalUpdatedAt).isNotNull();
        assertThat(updatedAt).isAfter(originalUpdatedAt);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT created_at FROM user_credential WHERE user_id = ?", Timestamp.class, user.id()))
                .isNotNull();
    }

    @Test
    void createsRoleWithTenantFromScopedServiceOperation() {
        Tenant tenant = tenantService.create("Scoped tenant");

        Role role = TenantContext.call(
                tenant.id(), () -> roleService.create(tenant.id(), "Scoped role", java.util.Set.of()));

        assertThat(jdbcTemplate.queryForObject("SELECT tenant_id FROM \"role\" WHERE id = ?", Long.class, role.id()))
                .isEqualTo(tenant.id());
    }

    @Test
    void paginatesRoleAndUserResponsesAgainstPostgres() {
        Tenant tenant = tenantService.create("Paged tenant");
        TenantContext.call(tenant.id(), () -> {
            Role role = roleService.create(tenant.id(), "Member manager", java.util.Set.of("tenant.user.manage"));
            for (int index = 0; index < 3; index++) {
                userService.create(
                        tenant.id(),
                        "member" + index + "@roseiot.test",
                        "Member " + index,
                        "StrongPass123!",
                        role.id());
            }
            return null;
        });

        Page<Role> firstRolePage = TenantContext.call(
                tenant.id(), () -> roleService.list(tenant.id(), PageRequest.of(0, 2, Sort.by("id"))));
        Page<UserResponse> firstUserPage = TenantContext.call(
                tenant.id(), () -> userService.list(tenant.id(), PageRequest.of(0, 2, Sort.by("id"))));
        Page<UserResponse> secondUserPage = TenantContext.call(
                tenant.id(), () -> userService.list(tenant.id(), PageRequest.of(1, 2, Sort.by("id"))));

        assertThat(firstRolePage.getTotalElements()).isEqualTo(1);
        assertThat(firstRolePage.getContent()).extracting(Role::name).containsExactly("Member manager");
        assertThat(firstUserPage.getTotalElements()).isEqualTo(3);
        assertThat(firstUserPage.getContent()).hasSize(2);
        assertThat(secondUserPage.getContent()).hasSize(1);
        assertThat(firstUserPage.getContent()).doesNotContainAnyElementsOf(secondUserPage.getContent());
    }

    private long insertRole(Long tenantId, String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO \"role\" (tenant_id, name) VALUES (?, ?) RETURNING id", Long.class, tenantId, name);
    }

    private long insertUser(Long tenantId, String email) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO \"user\" (tenant_id, email) VALUES (?, ?) RETURNING id", Long.class, tenantId, email);
    }
}
