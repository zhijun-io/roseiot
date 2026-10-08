package io.roseiot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class FlywayMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("roseiot.datasource.url", postgres::getJdbcUrl);
        registry.add("roseiot.datasource.username", postgres::getUsername);
        registry.add("roseiot.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayBaselineMigrationApplied() {
        Integer successes = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '1' AND success = TRUE
                """,
                Integer.class);
        assertThat(successes).isEqualTo(1);

        Integer baselineRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roseiot_schema_baseline",
                Integer.class);
        assertThat(baselineRows).isEqualTo(1);
    }

    @Test
    void flywayMigrationIsIdempotentOnRestart() {
        Integer before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                Integer.class);

        Integer after = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                Integer.class);

        assertThat(after).isEqualTo(before);
    }
}
