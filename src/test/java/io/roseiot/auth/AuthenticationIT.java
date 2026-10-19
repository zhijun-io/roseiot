package io.roseiot.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.roseiot.TestcontainersConfiguration;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import({TestcontainersConfiguration.class, AuthenticationIT.TestMailConfiguration.class})
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.docker.compose.enabled=false",
            "app.auth.jwt.hmac-secret=cm9zZWlvdC10ZXN0LWtleS1tdXN0LWJlLWF0LWxlYXN0LTMyLWJ5dGVz"
        })
class AuthenticationIT {

    private static final String PASSWORD = "password";

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private PostgreSQLContainer postgresContainer;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private CapturingResetEmailSender resetEmailSender;

    @BeforeEach
    void clearCapturedEmail() throws IOException, InterruptedException {
        resetEmailSender.clear();
        query("DELETE FROM auth_rate_limits");
    }

    @Test
    void loginDoesNotRevealWhetherAnEmailExists() throws Exception {
        var email = "admin-" + UUID.randomUUID() + "@example.invalid";
        seedSystemAdmin(email);

        var unknown = restTestClient
                .post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email + ".missing", PASSWORD))
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .returnResult();

        var wrongPassword = restTestClient
                .post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email, "incorrect-password"))
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .returnResult();

        assertThat(unknown.getResponseBody()).isEqualTo(wrongPassword.getResponseBody());
        assertThat(new String(unknown.getResponseBody())).contains("Invalid email or password");
    }

    @Test
    void firstLoginRequiresPasswordChangeAndNewAccessTokenReflectsCompletion() throws Exception {
        var email = "first-login-" + UUID.randomUUID() + "@example.invalid";
        seedSystemAdmin(email);

        LoginResponse firstLogin = restTestClient
                .post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email.toUpperCase(), PASSWORD))
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(firstLogin.passwordChangeRequired()).isTrue();
        assertThat(query("SELECT COUNT(*) FROM user_session_refresh_tokens WHERE token_digest = '"
                        + UserSessionService.digest(firstLogin.refreshToken()) + "'"))
                .isEqualTo("1");
        restTestClient
                .get()
                .uri("/api/me")
                .headers(headers -> headers.setBearerAuth(firstLogin.accessToken()))
                .exchange()
                .expectStatus()
                .isForbidden();

        PasswordChangeResponse changed = restTestClient
                .put()
                .uri("/api/me/password")
                .headers(headers -> headers.setBearerAuth(firstLogin.accessToken()))
                .body(new PasswordChangeRequest(PASSWORD, "NewPassword!456"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PasswordChangeResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(changed.passwordChangeRequired()).isFalse();
        restTestClient
                .get()
                .uri("/api/me")
                .headers(headers -> headers.setBearerAuth(changed.accessToken()))
                .exchange()
                .expectStatus()
                .isNotFound();

        restTestClient
                .post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email, "NewPassword!456"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.password_change_required")
                .isEqualTo(false);
    }

    @Test
    void passwordResetUsesGenericRequestResponseConsumesTokenOnceAndRevokesSessions() throws Exception {
        var email = "reset-" + UUID.randomUUID() + "@example.invalid";
        seedSystemAdmin(email);
        LoginResponse previousSession = restTestClient
                .post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email, PASSWORD))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody();

        var unknown = restTestClient
                .post()
                .uri("/api/auth/password-reset-requests")
                .body(new PasswordResetRequest("unknown-" + UUID.randomUUID() + "@example.invalid"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult();
        var known = restTestClient
                .post()
                .uri("/api/auth/password-reset-requests")
                .body(new PasswordResetRequest(email.toUpperCase()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult();
        assertThat(known.getResponseBody()).isEqualTo(unknown.getResponseBody());
        assertThat(new String(known.getResponseBody())).contains("If the account exists");

        String firstToken = resetToken(resetEmailSender.resetUrls().get(0));
        restTestClient
                .post()
                .uri("/api/auth/password-reset-requests")
                .body(new PasswordResetRequest(email))
                .exchange()
                .expectStatus()
                .isOk();
        String secondToken = resetToken(resetEmailSender.resetUrls().get(1));
        assertThat(query("SELECT COUNT(*) FROM password_reset_tokens WHERE token_digest = '"
                        + UserSessionService.digest(firstToken) + "' AND used_at IS NULL"))
                .isEqualTo("1");

        restTestClient
                .post()
                .uri("/api/auth/password-resets")
                .body(new PasswordResetCompletionRequest(firstToken, "RecoveredPassword!789"))
                .exchange()
                .expectStatus()
                .isOk();

        restTestClient
                .get()
                .uri("/api/me")
                .headers(headers -> headers.setBearerAuth(previousSession.accessToken()))
                .exchange()
                .expectStatus()
                .isUnauthorized();

        restTestClient
                .post()
                .uri("/api/auth/password-resets")
                .body(new PasswordResetCompletionRequest(secondToken, "AnotherPassword!987"))
                .exchange()
                .expectStatus()
                .isEqualTo(422);

        restTestClient
                .post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email, "RecoveredPassword!789"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.password_change_required")
                .isEqualTo(false);
    }

    @Test
    void passwordResetTokenExpires() throws Exception {
        var email = "reset-expired-" + UUID.randomUUID() + "@example.invalid";
        seedSystemAdmin(email);
        restTestClient
                .post()
                .uri("/api/auth/password-reset-requests")
                .body(new PasswordResetRequest(email))
                .exchange()
                .expectStatus()
                .isOk();
        String token = resetToken(resetEmailSender.resetUrls().getFirst());
        String digest = UserSessionService.digest(token);
        assertThat(query("UPDATE password_reset_tokens "
                        + "SET created_at = CURRENT_TIMESTAMP - INTERVAL '1 hour', "
                        + "expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' "
                        + "WHERE token_digest = '" + digest + "' RETURNING id"))
                .isNotBlank();

        restTestClient
                .post()
                .uri("/api/auth/password-resets")
                .body(new PasswordResetCompletionRequest(token, "RecoveredPassword!789"))
                .exchange()
                .expectStatus()
                .isEqualTo(422);
    }

    @Test
    void passwordResetCompletionRateLimitUsesPeerAddressAndReturnsGenericResponse() {
        for (int attempt = 0; attempt < 10; attempt++) {
            restTestClient
                    .post()
                    .uri("/api/auth/password-resets")
                    .body(new PasswordResetCompletionRequest("invalid-token", "RecoveredPassword!789"))
                    .exchange()
                    .expectStatus()
                    .isEqualTo(422);
        }

        restTestClient
                .post()
                .uri("/api/auth/password-resets")
                .header("X-Forwarded-For", "203.0.113.45")
                .body(new PasswordResetCompletionRequest("invalid-token", "RecoveredPassword!789"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader()
                .valueEquals("Retry-After", "900")
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Too many authentication attempts. Try again later.");
    }

    @Test
    void loginRateLimitIsSharedAndDoesNotRevealAccountExistence() {
        String email = "throttle-" + UUID.randomUUID() + "@example.invalid";
        for (int attempt = 0; attempt < 5; attempt++) {
            restTestClient
                    .post()
                    .uri("/api/auth/login")
                    .body(new LoginRequest(email, "wrong"))
                    .exchange()
                    .expectStatus()
                    .isUnauthorized();
        }
        restTestClient
                .post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email, "wrong"))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader()
                .valueEquals("Retry-After", "900")
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Too many authentication attempts. Try again later.");
    }

    private void seedSystemAdmin(String email) throws IOException, InterruptedException {
        var sql = """
                BEGIN;
                CREATE TEMPORARY TABLE test_user (id BIGINT NOT NULL);
                WITH created AS (
                    INSERT INTO users (tenant_id, email, display_name, status)
                    SELECT id, '%s', 'Integration Admin', 'ACTIVE' FROM tenants WHERE code = 'default'
                    RETURNING id
                )
                INSERT INTO test_user SELECT id FROM created;
                INSERT INTO user_credentials (user_id, password_hash, password_change_required)
                SELECT id, '%s', TRUE FROM test_user;
                INSERT INTO user_role (user_id, role_id)
                SELECT test_user.id, roles.id FROM test_user, roles JOIN tenants ON tenants.id = roles.tenant_id
                WHERE tenants.code = 'default' AND roles.code = 'SYSTEM_ADMIN';
                COMMIT;
                """.formatted(email, passwordEncoder.encode(PASSWORD));
        ExecResult result = postgresContainer.execInContainer(
                "psql",
                "-X",
                "-U",
                postgresContainer.getUsername(),
                "-d",
                postgresContainer.getDatabaseName(),
                "-v",
                "ON_ERROR_STOP=1",
                "-c",
                sql);
        assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
    }

    private String query(String sql) throws IOException, InterruptedException {
        ExecResult result = postgresContainer.execInContainer(
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

    private String resetToken(String resetUrl) {
        return resetUrl.substring(resetUrl.indexOf("token=") + "token=".length());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestMailConfiguration {
        @Bean
        @Primary
        CapturingResetEmailSender capturingResetEmailSender() {
            return new CapturingResetEmailSender();
        }
    }

    static class CapturingResetEmailSender implements PasswordResetEmailSender {
        private final java.util.List<String> resetUrls = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void send(String recipient, String resetUrl) {
            resetUrls.add(resetUrl);
        }

        java.util.List<String> resetUrls() {
            return java.util.List.copyOf(resetUrls);
        }

        void clear() {
            resetUrls.clear();
        }
    }

    private record LoginRequest(String email, String password) {}
}
