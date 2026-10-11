package io.roseiot.tenant;

import io.roseiot.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
@WithMockUser(authorities = {"tenant.create", "tenant.disable", "tenant.enable", "tenant.read"})
class TenantPersistencePostgresTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsTenantWithoutCreatingUsersRolesOrInvitations() {
        Tenant tenant = tenantService.create("Acme HVAC");

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tenant WHERE id = ?", String.class, tenant.id()))
                .isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE tenant_id = ?", Integer.class, tenant.id()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM \"role\" WHERE tenant_id = ?", Integer.class, tenant.id()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables"
                        + " WHERE table_schema = current_schema() AND table_name = 'user_invitation'", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM resource WHERE type = 'ACTION'", Integer.class))
                .isEqualTo(11);
    }

    @Test
    void updatesTenantById() {
        Tenant tenant = tenantService.create("Acme HVAC");

        tenantService.update(tenant.id(), "Acme Climate", true);

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM tenant WHERE id = ?", String.class, tenant.id()))
                .isEqualTo("Acme Climate");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM tenant WHERE id = ?", String.class, tenant.id()))
                .isEqualTo("DISABLED");
    }
}
