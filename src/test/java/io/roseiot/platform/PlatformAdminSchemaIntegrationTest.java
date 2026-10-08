package io.roseiot.platform;

import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.test.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class PlatformAdminSchemaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = PostgresTestSupport.newContainer();

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        PostgresTestSupport.registerDatasource(registry, postgres);
    }

    @Autowired
    PlatformAdminMapper platformAdminMapper;

    @Test
    void platformAdminTableExistsAndIsEmpty() {
        assertThat(platformAdminMapper.selectCount(null)).isZero();
    }
}
