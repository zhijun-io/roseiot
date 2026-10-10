package io.roseiot.iam.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserCredentialMigrationPostgresTest {

    @Test
    void shouldMovePasswordAndLatestResetRequestIntoUserCredential() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("6"))
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            UUID userId = UUID.randomUUID();
            UUID tenantId = jdbc.queryForObject("SELECT id FROM tenant WHERE slug = 'default'", UUID.class);
            jdbc.update("INSERT INTO users (id,tenant_id,email,password_hash,password_algorithm) VALUES (?,?,?,?,?)",
                    userId, tenantId, "migration@example.test", "existing-bcrypt-hash", "bcrypt");
            Instant older = Instant.parse("2026-10-01T00:00:00Z");
            Instant latest = Instant.parse("2026-10-02T00:00:00Z");
            jdbc.update("INSERT INTO password_reset_request (id,user_id,token_hash,expires_at,created_at)"
                            + " VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), userId, "a".repeat(64), Timestamp.from(older.plusSeconds(3600)), Timestamp.from(older));
            jdbc.update("INSERT INTO password_reset_request (id,user_id,token_hash,expires_at,created_at)"
                            + " VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), userId, "b".repeat(64), Timestamp.from(latest.plusSeconds(3600)), Timestamp.from(latest));

            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            assertThat(jdbc.queryForMap("SELECT * FROM user_credential WHERE user_id = ?", userId))
                    .containsEntry("password_hash", "existing-bcrypt-hash")
                    .containsEntry("reset_token_hash", "b".repeat(64))
                    .containsEntry("reset_at", null);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns"
                            + " WHERE table_name = 'users' AND column_name IN ('password_hash','password_algorithm')",
                    Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT to_regclass('password_reset_request') IS NULL", Boolean.class)).isTrue();
        }
    }
}
