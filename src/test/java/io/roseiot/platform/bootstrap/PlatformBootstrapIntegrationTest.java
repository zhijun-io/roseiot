package io.roseiot.platform.bootstrap;

import io.roseiot.platform.persistence.PlatformAdmin;
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
@SpringBootTest
class PlatformBootstrapIntegrationTest {

    private static final String RAW_EMAIL = "Admin@Example.com";
    private static final String PASSWORD = "password1234";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    BootstrapPlatformAdminUseCase bootstrapPlatformAdminUseCase;

    @Autowired
    PlatformAdminMapper platformAdminMapper;

    @Test
    void bootstrapCreatesExactlyOnePlatformAdmin() {
        bootstrapPlatformAdminUseCase.bootstrap(RAW_EMAIL, PASSWORD);

        assertThat(platformAdminMapper.selectCount(null)).isEqualTo(1);
        PlatformAdmin stored = platformAdminMapper.selectList(null).getFirst();
        assertThat(stored.getEmail()).isEqualTo("admin@example.com");
        assertThat(stored.getPasswordHash()).isNotBlank();
        assertThat(stored.getPasswordHash()).doesNotContain(PASSWORD);
        assertThat(stored.getPasswordAlgorithm()).isEqualTo("bcrypt");
        assertThat(stored.getId()).isNotNull();
        assertThat(stored.getId().version()).isEqualTo(7);
        assertThat(stored.getCreatedAt()).isNotNull();
    }
}
