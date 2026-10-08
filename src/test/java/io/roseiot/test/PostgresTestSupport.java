package io.roseiot.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

public final class PostgresTestSupport {

    public static final String POSTGRES_IMAGE = "postgres:16-alpine";

    private PostgresTestSupport() {
    }

    public static PostgreSQLContainer<?> newContainer() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE);
    }

    public static void registerDatasource(DynamicPropertyRegistry registry, PostgreSQLContainer<?> postgres) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    public static String[] jdbcCommandLineArgs(PostgreSQLContainer<?> postgres) {
        return new String[] {
            "--spring.datasource.url=" + postgres.getJdbcUrl(),
            "--spring.datasource.username=" + postgres.getUsername(),
            "--spring.datasource.password=" + postgres.getPassword()
        };
    }
}
