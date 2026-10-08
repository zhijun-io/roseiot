package io.roseiot.platform.bootstrap;

import io.roseiot.RoseiotApplication;
import io.roseiot.platform.persistence.PlatformAdmin;
import io.roseiot.platform.persistence.PlatformAdminMapper;
import io.roseiot.test.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class PlatformBootstrapIntegrationTest {

    private static final String RAW_EMAIL = "Admin@Example.com";
    private static final String PASSWORD = "password1234";

    @Container
    static PostgreSQLContainer<?> postgres = PostgresTestSupport.newContainer();

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        PostgresTestSupport.registerDatasource(registry, postgres);
    }

    @Autowired
    BootstrapPlatformAdminUseCase bootstrapPlatformAdminUseCase;

    @Autowired
    PlatformAdminMapper platformAdminMapper;

    @BeforeEach
    void clearPlatformAdmins() {
        platformAdminMapper.delete(null);
    }

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

    @Test
    void refusesSecondBootstrapWhenPlatformAdminExists() {
        bootstrapPlatformAdminUseCase.bootstrap(RAW_EMAIL, PASSWORD);

        assertThatThrownBy(() -> bootstrapPlatformAdminUseCase.bootstrap("other@example.com", "anotherPass1"))
                .isInstanceOf(AlreadyBootstrappedException.class)
                .hasMessageContaining("already bootstrapped");

        assertThat(platformAdminMapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void duplicateBootstrapCliPathExitsNonZeroAndKeepsSingleAdmin() {
        String secondPassword = "anotherPass1";
        bootstrapPlatformAdminUseCase.bootstrap("first@example.com", PASSWORD);

        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
        int exitCode;
        try {
            exitCode = RoseiotApplication.runBootstrapCommand(Stream.concat(
                    Arrays.stream(PostgresTestSupport.jdbcCommandLineArgs(postgres)),
                    Stream.of(
                            "--BOOTSTRAP_ADMIN_EMAIL=second@example.com",
                            "--BOOTSTRAP_ADMIN_PASSWORD=" + secondPassword))
                    .toArray(String[]::new));
        } finally {
            System.setErr(originalErr);
        }

        String stderr = capturedErr.toString(StandardCharsets.UTF_8);
        assertThat(exitCode).isNotZero();
        assertThat(stderr).contains("already bootstrapped").doesNotContain(secondPassword);
        assertThat(platformAdminMapper.selectCount(null)).isEqualTo(1);
        assertThat(platformAdminMapper.selectList(null).getFirst().getEmail()).isEqualTo("first@example.com");
    }
}
