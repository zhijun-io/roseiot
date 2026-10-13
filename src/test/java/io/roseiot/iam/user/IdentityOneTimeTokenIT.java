package io.roseiot.iam.user;

import static org.assertj.core.api.Assertions.assertThat;

import io.roseiot.TestcontainersConfig;
import io.roseiot.shared.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfig.class)
class IdentityOneTimeTokenIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserCredentialService credentialService;

    @Test
    void concurrentPasswordResetRequestsConsumeTheTokenAtMostOnce() throws Exception {
        Long userId = insertUser();
        String rawToken = "concurrent-reset-token-" + System.nanoTime();
        jdbcTemplate.update(
                "UPDATE user_credential SET reset_token = ?, reset_expires_at = ?" + " WHERE user_id = ?",
                hash(rawToken),
                Timestamp.from(Instant.now().plusSeconds(3600)),
                userId);

        AtomicInteger successes = race(() -> {
            credentialService.confirmPasswordReset(rawToken, "new secure password 9012");
            return true;
        });

        assertThat(successes).hasValue(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM user_credential WHERE reset_token = ? AND reset_at IS NOT NULL",
                        Integer.class,
                        hash(rawToken)))
                .isEqualTo(1);
    }

    private Long insertUser() {
        Long userId = System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO \"user\" (id,tenant_id,email) VALUES (?,(SELECT id FROM tenant WHERE code = 'default'),?)",
                userId,
                userId + "@example.test");
        jdbcTemplate.update(
                "INSERT INTO user_credential (user_id,password,created_at,updated_at) VALUES (?,?,?,?)",
                userId,
                "test-only-hash",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now()));
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

    private static String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        boolean run();
    }
}
