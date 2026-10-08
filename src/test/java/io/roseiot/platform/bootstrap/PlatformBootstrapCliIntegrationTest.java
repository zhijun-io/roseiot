package io.roseiot.platform.bootstrap;

import io.roseiot.platform.persistence.PlatformAdminMapper;
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
@SpringBootTest(properties = "roseiot.bootstrap.enabled=true")
class PlatformBootstrapCliIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerBootstrap(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("BOOTSTRAP_ADMIN_EMAIL", () -> "cli@example.com");
        registry.add("BOOTSTRAP_ADMIN_PASSWORD", () -> "password1234");
    }

    @Autowired
    PlatformAdminMapper platformAdminMapper;

    @Test
    void cliRunnerCreatesPlatformAdminOnStartup() {
        assertThat(platformAdminMapper.selectCount(null)).isEqualTo(1);
        assertThat(platformAdminMapper.selectList(null).getFirst().getEmail()).isEqualTo("cli@example.com");
    }
}
