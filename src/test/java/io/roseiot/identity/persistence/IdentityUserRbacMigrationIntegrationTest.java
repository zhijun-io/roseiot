package io.roseiot.identity.persistence;

import io.roseiot.AbstractIt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Flyway V4/V5：空库迁移后 users + RBAC 表就绪，且无 platform_admin（AC-1）。 */
class IdentityUserRbacMigrationIntegrationTest extends AbstractIt {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserMapper userMapper;

    @Autowired
    UserRefreshSessionMapper refreshSessionMapper;

    @BeforeEach
    void clearUserData() {
        refreshSessionMapper.delete(null);
        jdbcTemplate.update("DELETE FROM user_role");
        userMapper.delete(null);
    }

    @Test
    void contextLoads() {}

    @Test
    void usersTableExistsAndIsEmptyAfterCleanup() {
        assertThat(userMapper.selectCount(null)).isZero();
    }

    @Test
    void rbacTablesExistAndLegacyPlatformAdminTableDropped() {
        assertThat(tableExists("users")).isTrue();
        assertThat(tableExists("permission")).isTrue();
        assertThat(tableExists("role")).isTrue();
        assertThat(tableExists("role_permission")).isTrue();
        assertThat(tableExists("user_role")).isTrue();
        assertThat(tableExists("user_refresh_session")).isTrue();
        assertThat(tableExists("user_recovery_request")).isTrue();
        assertThat(tableExists("platform_admin")).isFalse();
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """,
                Integer.class,
                tableName);
        return count != null && count == 1;
    }
}
