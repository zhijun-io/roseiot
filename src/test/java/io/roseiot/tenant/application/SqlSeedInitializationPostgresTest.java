package io.roseiot.tenant.application;

import io.roseiot.TestcontainersConfig;
import io.roseiot.iam.domain.ActionCode;
import io.roseiot.tenant.infra.invitation.TenantInvitationNotifier;
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

import static org.assertj.core.api.Assertions.assertThat;

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
