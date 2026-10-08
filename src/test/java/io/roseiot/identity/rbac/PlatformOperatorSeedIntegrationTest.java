package io.roseiot.identity.rbac;

import io.roseiot.AbstractIt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** AC-14：PlatformOperator seed 不含 device.read / device.credentials.read。 */
class PlatformOperatorSeedIntegrationTest extends AbstractIt {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void platformOperatorDoesNotIncludeDeviceReadPermissions() {
        List<String> keys = jdbcTemplate.queryForList(
                """
                SELECT p.code
                FROM "role" r
                JOIN role_permission rp ON rp.role_id = r.id
                JOIN permission p ON p.id = rp.permission_id
                WHERE r.code = 'PlatformOperator'
                ORDER BY p.code
                """,
                String.class);

        assertThat(keys).isNotEmpty();
        assertThat(keys).doesNotContain("device.read", "device.credentials.read");
        assertThat(keys).contains("identity.profile.read", "identity.tenant.create");
    }
}
