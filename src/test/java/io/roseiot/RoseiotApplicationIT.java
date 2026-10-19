package io.roseiot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.docker.compose.enabled=false",
            "app.auth.jwt.hmac-secret=cm9zZWlvdC10ZXN0LWtleS1tdXN0LWJlLWF0LWxlYXN0LTMyLWJ5dGVz"
        })
class RoseiotApplicationIT {

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private PostgreSQLContainer postgresContainer;

    @Test
    void healthAndProbeEndpointsAreAvailable() {
        restTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP")
                .jsonPath("$.details")
                .doesNotExist();

        restTestClient
                .get()
                .uri("/actuator/health/liveness")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP")
                .jsonPath("$.components.db")
                .doesNotExist();

        restTestClient
                .get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP")
                .jsonPath("$.components.db.status")
                .isEqualTo("UP");
    }

    @Test
    void flywaySeedsProtectedDefaultTenantAndSystemAdminRole() throws Exception {
        assertThat(query("SELECT COUNT(*) FROM tenants WHERE code = 'default'")).isEqualTo("1");
        assertThat(query("""
                        SELECT COUNT(*)
                        FROM roles AS role
                        JOIN tenants AS tenant ON tenant.id = role.tenant_id
                        WHERE tenant.code = 'default'
                          AND role.code = 'SYSTEM_ADMIN'
                          AND role.protected
                        """)).isEqualTo("1");
        assertThat(query("SELECT COUNT(*) FROM role_resource")).isEqualTo("0");
    }

    @Test
    void bootstrapSqlCreatesExactlyOneAdminAtomicallyAndRejectsReplayAndConcurrency() throws Exception {
        var bootstrapSql = Files.readString(Path.of("scripts/bootstrap-system-admin.sql"));
        var invalidHash = runBootstrap(bootstrapSql, "invalid-" + UUID.randomUUID(), "plaintext-password");
        assertThat(invalidHash.getExitCode()).isNotZero();
        assertThat(query("SELECT COUNT(*) FROM users")).isEqualTo("0");
        assertThat(query("SELECT COUNT(*) FROM audit_records")).isEqualTo("0");

        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = bootstrapAttempt(bootstrapSql, barrier, "first-" + UUID.randomUUID());
            var second = bootstrapAttempt(bootstrapSql, barrier, "second-" + UUID.randomUUID());
            var results = executor.invokeAll(List.of(first, second));
            var successfulAttempts = 0;
            var failedAttempts = 0;
            for (var result : results) {
                if (result.get().getExitCode() == 0) {
                    successfulAttempts++;
                } else {
                    failedAttempts++;
                    assertThat(result.get().getStderr())
                            .contains("Initial system administrator has already been created");
                }
            }

            assertThat(successfulAttempts).isEqualTo(1);
            assertThat(failedAttempts).isEqualTo(1);
            assertThat(query("SELECT COUNT(*) FROM users")).isEqualTo("1");
            assertThat(query("SELECT COUNT(*) FROM user_credentials WHERE password_change_required"))
                    .isEqualTo("1");
            assertThat(query("""
                            SELECT COUNT(*)
                            FROM user_role AS assignment
                            JOIN roles AS role ON role.id = assignment.role_id
                            WHERE role.code = 'SYSTEM_ADMIN'
                            """)).isEqualTo("1");
            assertThat(query("SELECT COUNT(*) FROM audit_records WHERE action = 'bootstrap.system-admin.initialize'"))
                    .isEqualTo("1");

            var replay = runBootstrap(bootstrapSql, "replay-" + UUID.randomUUID());
            assertThat(replay.getExitCode()).isNotZero();
            assertThat(replay.getStderr()).contains("Initial system administrator has already been created");
            assertThat(query("SELECT COUNT(*) FROM users")).isEqualTo("1");
            assertThat(query("SELECT COUNT(*) FROM audit_records WHERE action = 'bootstrap.system-admin.initialize'"))
                    .isEqualTo("1");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void noPublicSetupEndpointIsExposed() {
        restTestClient.post().uri("/api/setup").exchange().expectStatus().isUnauthorized();
    }

    private Callable<ExecResult> bootstrapAttempt(String sql, CyclicBarrier barrier, String name) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return runBootstrap(sql, name);
        };
    }

    private ExecResult runBootstrap(String sql, String name) throws IOException, InterruptedException {
        return runBootstrap(sql, name, "$2a$10$" + "x".repeat(53));
    }

    private ExecResult runBootstrap(String sql, String name, String passwordHash)
            throws IOException, InterruptedException {
        var script = substitutePsqlVariables(
                sql,
                Map.of(
                        "email", name + "@example.invalid",
                        "display_name", "System Admin O'Connor " + name,
                        "password_hash", passwordHash));
        return postgresContainer.execInContainer(
                "psql",
                "-X",
                "-U",
                postgresContainer.getUsername(),
                "-d",
                postgresContainer.getDatabaseName(),
                "-v",
                "ON_ERROR_STOP=1",
                "-c",
                script);
    }

    private String substitutePsqlVariables(String sql, Map<String, String> values) {
        var matcher = Pattern.compile(":'([a-z_]+)'").matcher(sql);
        var result = new StringBuffer();
        while (matcher.find()) {
            var value = values.get(matcher.group(1));
            if (value == null) {
                throw new IllegalArgumentException("Missing psql variable: " + matcher.group(1));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement("'" + value.replace("'", "''") + "'"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String query(String sql) throws IOException, InterruptedException {
        var result = postgresContainer.execInContainer(
                "psql",
                "-X",
                "-U",
                postgresContainer.getUsername(),
                "-d",
                postgresContainer.getDatabaseName(),
                "-At",
                "-c",
                sql);
        assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
        return result.getStdout().strip();
    }
}
