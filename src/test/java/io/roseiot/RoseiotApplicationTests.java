package io.roseiot;

import io.roseiot.test.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class RoseiotApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = PostgresTestSupport.newContainer();

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        PostgresTestSupport.registerDatasource(registry, postgres);
    }

    @Test
    void contextLoads() {
    }
}
