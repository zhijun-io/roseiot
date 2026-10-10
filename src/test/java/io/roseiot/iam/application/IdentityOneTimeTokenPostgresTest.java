package io.roseiot.iam.application;

import io.roseiot.TestcontainersConfig;
import io.roseiot.iam.domain.TokenDigests;
import io.roseiot.shared.domain.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfig.class)
class IdentityOneTimeTokenPostgresTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserCredentialService credentialService;

    @Test
    void concurrentPasswordResetRequestsConsumeTheTokenAtMostOnce() throws Exception {
        UUID userId = insertUser();
        String rawToken = "concurrent-reset-token-" + UUID.randomUUID();
        jdbcTemplate.update("UPDATE user_credential SET reset_token = ?, reset_expires_at = ?"
                        + " WHERE user_id = ?",
                TokenDigests.sha256Hex(rawToken), Timestamp.from(Instant.now().plusSeconds(3600)), userId);

        AtomicInteger successes = race(() -> {
            credentialService.confirmPasswordReset(rawToken, "new secure password 9012");
            return true;
        });

        assertThat(successes).hasValue(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_credential WHERE reset_token = ? AND reset_at IS NOT NULL",
                Integer.class, TokenDigests.sha256Hex(rawToken))).isEqualTo(1);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id,tenant_id,email) VALUES (?,(SELECT id FROM tenant WHERE slug = 'default'),?)",
                userId, userId + "@example.test");
        jdbcTemplate.update("INSERT INTO user_credential (user_id,password,created_at,updated_at) VALUES (?,?,?,?)",
                userId, "test-only-hash", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        return userId;
    }

    private static AtomicInteger race(CheckedAction action) throws Exception {
        int contenders = 2;
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        try (ExecutorService executor = Executors.newFixedThreadPool(contenders)) {
            Future<?>[] futures = new Future<?>[contenders];
            for (int index = 0; index < contenders; index++) {
                futures[index] = executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent start");
                    }
                    try {
                        if (action.run()) {
                            successes.incrementAndGet();
                        }
                    } catch (BusinessException expectedForLosingRequest) {
                        // The losing concurrent reset request is rejected by the business boundary.
                    }
                    return null;
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }
        return successes;
    }

    @FunctionalInterface
    private interface CheckedAction {
        boolean run();
    }
}
