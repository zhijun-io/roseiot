package io.roseiot.identity.bootstrap;

import io.roseiot.AbstractIt;
import io.roseiot.RoseiotApplication;
import io.roseiot.identity.application.BootstrapUserService;
import io.roseiot.identity.persistence.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 引导 CLI 路径（需真实 Postgres + 独立 Spring 进程），用例规则见 {@link BootstrapUserServiceTests}。 */
class BootstrapDuplicateCliTests extends AbstractIt {

    private static final String PASSWORD = "password1234";

    @Autowired
    PostgreSQLContainer<?> postgresContainer;

    @Autowired
    BootstrapUserService bootstrapUserService;

    @Autowired
    UserMapper userMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearPlatformUsers() {
        jdbcTemplate.update("DELETE FROM user_role");
        userMapper.delete(null);
    }

    @Test
    void duplicateBootstrapCliPathExitsNonZeroAndKeepsSingleUser() {
        String secondPassword = "anotherPass1";
        bootstrapUserService.bootstrap("first@example.com", PASSWORD);

        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
        int exitCode;
        try {
            exitCode = RoseiotApplication.runBootstrapCommand(Stream.of(
                            "--spring.datasource.url=" + postgresContainer.getJdbcUrl(),
                            "--spring.datasource.username=" + postgresContainer.getUsername(),
                            "--spring.datasource.password=" + postgresContainer.getPassword(),
                            "--BOOTSTRAP_USER_EMAIL=second@example.com",
                            "--BOOTSTRAP_USER_PASSWORD=" + secondPassword)
                    .toArray(String[]::new));
        } finally {
            System.setErr(originalErr);
        }

        String stderr = capturedErr.toString(StandardCharsets.UTF_8);
        assertThat(exitCode).isNotZero();
        assertThat(stderr).contains("already bootstrapped").doesNotContain(secondPassword);
        assertThat(userMapper.selectCount(null)).isEqualTo(1);
        assertThat(userMapper.selectList(null).getFirst().getEmail()).isEqualTo("first@example.com");
    }
}
