package io.roseiot.iam.user;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseBaselinePostgresTest {

    @Test
    void createsCurrentSchemaAndSeedsWithOneBaselineMigration() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load();

            flyway.migrate();

            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
            assertThat(flyway.info().applied()).hasSize(1);
            assertThat(flyway.info().applied()[0].getVersion().getVersion()).isEqualTo("1");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns"
                            + " WHERE table_schema = current_schema() AND table_name = 'users'"
                            + " AND column_name IN ('name', 'updated_at', 'status')", Integer.class))
                    .isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns"
                            + " WHERE table_schema = current_schema() AND table_name = 'user_credential'"
                            + " AND column_name IN ('password', 'reset_token', 'reset_expires_at', 'reset_at')",
                    Integer.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns"
                            + " WHERE table_schema = current_schema() AND table_name = 'role' AND column_name = 'code'",
                    Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns"
                            + " WHERE table_schema = current_schema() AND table_name = 'role' AND column_name = 'built_in'",
                    Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns"
                            + " WHERE table_schema = current_schema() AND table_name = 'tenant' AND column_name = 'slug'",
                    Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_constraint"
                            + " WHERE connamespace = current_schema()::regnamespace AND contype = 'f'",
                    Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns"
                            + " WHERE table_schema = current_schema() AND table_name = 'resource' AND column_name = 'id'",
                    String.class)).isEqualTo("bigint");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns"
                            + " WHERE table_schema = current_schema() AND table_name = 'system_setting' AND column_name = 'id'",
                    Integer.class)).isEqualTo(1);
        }
    }
}
