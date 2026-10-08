package io.roseiot.platform;

import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.platform.persistence.PlatformAdminRefreshSessionMapper;
import io.roseiot.AbstractIt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Flyway 迁移与平台表存在性（Q136：空库 → 最新版本）。 */
class PlatformFlywaySchemaTests extends AbstractIt {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformAdminMapper platformAdminMapper;

    @Autowired
    PlatformAdminRefreshSessionMapper refreshSessionMapper;

    @BeforeEach
    void clearPlatformAdminData() {
        refreshSessionMapper.delete(null);
        platformAdminMapper.delete(null);
    }

    @Test
    void contextLoads() {}

    @Test
    void platformAdminTableExistsAndIsEmpty() {
        assertThat(platformAdminMapper.selectCount(null)).isZero();
    }

    @Test
    void authSessionAndRecoveryTablesExist() {
        assertThat(tableExists("platform_admin_refresh_session")).isTrue();
        assertThat(tableExists("platform_admin_recovery_request")).isTrue();
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
