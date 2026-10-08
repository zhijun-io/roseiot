package io.roseiot.identity.persistence;

import io.roseiot.AbstractIt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** 空库应用当前基线后，User、会话和 RBAC 表均可用。 */
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
    void identityTablesExist() {
        assertThat(tableExists("users")).isTrue();
        assertThat(tableExists("permission")).isTrue();
        assertThat(tableExists("role")).isTrue();
        assertThat(tableExists("role_permission")).isTrue();
        assertThat(tableExists("user_role")).isTrue();
        assertThat(tableExists("user_refresh_session")).isTrue();
        assertThat(tableExists("user_recovery_request")).isTrue();
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
