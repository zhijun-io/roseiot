package io.roseiot.identity.persistence;

import io.roseiot.TestcontainersConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** AC-3：自 V3 数据迁移至 V4 后 users 与会话 user_id 一致。 */
@Testcontainers
class V3UserMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TestcontainersConfig.POSTGRES_IMAGE);

    @Test
    void migratesLegacyPlatformAdminRowsToUsers() throws Exception {
        String jdbcUrl = POSTGRES.getJdbcUrl();
        String username = POSTGRES.getUsername();
        String password = POSTGRES.getPassword();

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .target("3")
                .load();
        flyway.migrate();

        UUID userId = UUID.fromString("30000000-0000-4000-8000-000000000001");
        UUID sessionId = UUID.fromString("30000000-0000-4000-8000-000000000002");
        UUID familyId = UUID.fromString("30000000-0000-4000-8000-000000000003");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            try (PreparedStatement insertAdmin = connection.prepareStatement(
                    """
                    INSERT INTO platform_admin (id, email, password_hash, password_algorithm, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                insertAdmin.setObject(1, userId);
                insertAdmin.setString(2, "legacy@example.com");
                insertAdmin.setString(3, "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
                insertAdmin.setString(4, "bcrypt");
                insertAdmin.setTimestamp(5, Timestamp.from(now));
                insertAdmin.executeUpdate();
            }
            try (PreparedStatement insertSession = connection.prepareStatement(
                    """
                    INSERT INTO platform_admin_refresh_session
                        (id, platform_admin_id, family_id, token_hash, expires_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                insertSession.setObject(1, sessionId);
                insertSession.setObject(2, userId);
                insertSession.setObject(3, familyId);
                insertSession.setString(4, "a".repeat(64));
                insertSession.setTimestamp(5, Timestamp.from(now.plusSeconds(3600)));
                insertSession.setTimestamp(6, Timestamp.from(now));
                insertSession.executeUpdate();
            }
        }

        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            try (PreparedStatement users = connection.prepareStatement(
                    "SELECT email FROM users WHERE id = ?")) {
                users.setObject(1, userId);
                try (ResultSet rs = users.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("email")).isEqualTo("legacy@example.com");
                }
            }
            try (PreparedStatement refresh = connection.prepareStatement(
                    "SELECT user_id FROM user_refresh_session WHERE id = ?")) {
                refresh.setObject(1, sessionId);
                try (ResultSet rs = refresh.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getObject("user_id", UUID.class)).isEqualTo(userId);
                }
            }
            try (PreparedStatement dropped = connection.prepareStatement(
                    """
                    SELECT count(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = 'platform_admin'
                    """)) {
                try (ResultSet rs = dropped.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isZero();
                }
            }
        }
    }
}
