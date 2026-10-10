package io.roseiot.tenant.application;

import io.roseiot.TestcontainersConfig;
import io.roseiot.iam.domain.ActionCode;
import io.roseiot.iam.application.RoleService;
import io.roseiot.iam.persistence.UserRoleMapper;
import io.roseiot.shared.domain.BusinessException;
import io.roseiot.shared.mybatis.TenantContext;
import io.roseiot.tenant.infra.notifier.TenantInvitationNotifier;
import io.roseiot.tenant.persistence.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class SqlSeedInitializationPostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @MockitoBean
    private TenantInvitationNotifier tenantInvitationNotifier;

    @Test
    void flywaySeedsDefaultTenantCatalogueAndPlatformRoles() {
        UUID defaultTenantId = jdbc.queryForObject(
                "SELECT id FROM tenant WHERE slug = 'default'", UUID.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource WHERE tenant_id = ? AND type = 'MENU'",
                Integer.class, defaultTenantId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource WHERE tenant_id = ? AND type = 'ACTION'",
                Integer.class, defaultTenantId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource WHERE status = 'ACTIVE'",
                Integer.class)).isEqualTo(13);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns"
                        + " WHERE table_name = 'resource' AND column_name IN ('scope','active')",
                Integer.class)).isZero();

        List<String> adminActions = jdbc.queryForList("""
                SELECT a.code FROM role_resource rr
                JOIN "role" r ON r.id = rr.role_id
                JOIN resource a ON a.id = rr.resource_id
                WHERE r.tenant_id = ? AND r.code = 'TENANT_ADMIN'
                """, String.class, defaultTenantId);
        assertThat(adminActions).containsExactlyInAnyOrder(
                ActionCode.TENANT_CREATE, ActionCode.TENANT_DISABLE, ActionCode.TENANT_ENABLE);
    }

    @Test
    void tenantRolesCannotReceivePlatformOnlyActions() {
        Tenant tenant = tenantService.create("Platform Guard Test", "platform-guard-test", "admin@platform-guard.test");
        assertThatThrownBy(() -> TenantContext.call(tenant.getId(), () -> roleService.create(tenant.getId(),
                "PLATFORM_ESCALATION", "Escalation", Set.of(ActionCode.TENANT_CREATE))))
                .isInstanceOf(BusinessException.class);

        UUID roleId = jdbc.queryForObject("SELECT id FROM \"role\" WHERE tenant_id = ? AND code = 'TENANT_ADMIN'",
                UUID.class, tenant.getId());
        UUID userId = UUID.randomUUID();
        UUID defaultTenantId = jdbc.queryForObject("SELECT id FROM tenant WHERE slug = 'default'", UUID.class);
        UUID platformActionId = jdbc.queryForObject("SELECT id FROM resource WHERE tenant_id = ? AND code = ?",
                UUID.class, defaultTenantId, ActionCode.TENANT_CREATE);
        Instant now = Instant.now();
        jdbc.update("INSERT INTO users (id,tenant_id,email,status,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                userId, tenant.getId(), "platform-guard-user@roseiot.io", "ACTIVE",
                Timestamp.from(now), Timestamp.from(now));
        userRoleMapper.insertAssignment(UUID.randomUUID(), userId, roleId, tenant.getId());
        jdbc.update("INSERT INTO role_resource (tenant_id,role_id,resource_id) VALUES (?,?,?)",
                tenant.getId(), roleId, platformActionId);

        assertThat(userRoleMapper.listPermissionCodesForUser(userId)).doesNotContain(ActionCode.TENANT_CREATE);
    }

    @Test
    void tenantApplicationCreatesBuiltInRolesAndActionsWithoutDatabaseTrigger() {
        Tenant tenant = tenantService.create("App Seed Test", "app-seed-test", "admin@app-seed.test");
        UUID tenantId = tenant.getId();

        assertThat(jdbc.queryForList("SELECT code FROM \"role\" WHERE tenant_id = ? AND built_in ORDER BY code",
                String.class, tenantId)).containsExactly("DEVICE_OPERATOR", "DEVICE_VIEWER", "TENANT_ADMIN");
        List<String> adminActions = jdbc.queryForList("""
                SELECT a.code FROM role_resource rr
                JOIN "role" r ON r.id = rr.role_id
                JOIN resource a ON a.id = rr.resource_id
                WHERE r.tenant_id = ? AND r.code = 'TENANT_ADMIN'
                """, String.class, tenantId);
        assertThat(adminActions).containsExactlyInAnyOrder(
                ActionCode.TENANT_USER_MANAGE, ActionCode.TENANT_USER_INVITE, ActionCode.TENANT_ROLE_MANAGE);
    }

    @Test
    void directTenantInsertDoesNotRunDatabaseSeedCode() {
        UUID tenantId = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, slug, name) VALUES (?, ?, ?)", tenantId, "direct-tenant", "Direct");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"role\" WHERE tenant_id = ?",
                Integer.class, tenantId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_trigger
                WHERE NOT tgisinternal AND tgrelid = 'tenant'::regclass
                    AND tgname = 'tenant_seed_builtin_roles'
                """, Integer.class)).isZero();
    }

    @Test
    void tenantAndApplicationSeedRollbackTogether() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tx.execute(status -> {
                    tenantService.create("Rollback", "app-seed-rollback", "admin@rollback.test");
                    throw new IllegalStateException("force transaction rollback");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("force transaction rollback");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant WHERE slug = 'app-seed-rollback'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"role\" WHERE tenant_id IN "
                + "(SELECT id FROM tenant WHERE slug = 'app-seed-rollback')", Integer.class))
                .isZero();
    }

    @Test
    void startupDoesNotRequireOrSeedAPlatformAdministrator() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = 'platform@roseiot.io'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name = 'platform_configuration'
                """, Integer.class)).isZero();
    }
}
