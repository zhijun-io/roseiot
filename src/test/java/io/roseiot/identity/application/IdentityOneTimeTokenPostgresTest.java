package io.roseiot.identity.application;

import io.roseiot.TestcontainersConfig;
import io.roseiot.identity.domain.TokenDigests;
import io.roseiot.identity.domain.IdentityJwtClaims;
import io.roseiot.identity.infra.security.IdentityJwtAuthenticationConverter;
import io.roseiot.identity.infra.security.InvalidRefreshTokenAuthenticationException;
import io.roseiot.shared.domain.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

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
    private UserSessionService sessionService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private IdentityJwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void concurrentRefreshRequestsRotateAtMostOnceAndRevokeTheFamily() throws Exception {
        UUID userId = insertUser();
        UUID sessionId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawToken = "concurrent-refresh-token-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO user_refresh_session"
                        + " (id,user_id,family_id,token_hash,expires_at,created_at) VALUES (?,?,?,?,?,?)",
                sessionId, userId, familyId, TokenDigests.sha256Hex(rawToken),
                Timestamp.from(Instant.now().plusSeconds(3600)), Timestamp.from(Instant.now()));

        AtomicInteger successes = race(() -> sessionService.refresh(rawToken) != null);

        assertThat(successes).hasValue(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_refresh_session WHERE family_id = ? AND revoked_at IS NULL",
                Integer.class, familyId)).isZero();
    }

    @Test
    void concurrentPasswordResetRequestsConsumeTheTokenAtMostOnce() throws Exception {
        UUID userId = insertUser();
        String rawToken = "concurrent-reset-token-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO password_reset_request"
                        + " (id,user_id,token_hash,expires_at,created_at) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), userId, TokenDigests.sha256Hex(rawToken),
                Timestamp.from(Instant.now().plusSeconds(3600)), Timestamp.from(Instant.now()));

        AtomicInteger successes = race(() -> {
            passwordResetService.confirmPasswordReset(rawToken, "new secure password 9012");
            return true;
        });

        assertThat(successes).hasValue(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT authorization_version FROM users WHERE id = ?", Integer.class, userId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM password_reset_request WHERE token_hash = ? AND used_at IS NOT NULL",
                Integer.class, TokenDigests.sha256Hex(rawToken))).isEqualTo(1);
    }

    @Test
    void rejectsAccessTokenForRevokedSession() {
        UUID userId = insertUser();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        insertSession(userId, sessionId, now.plusSeconds(3600), now);

        assertRejectedSessionToken(userId, sessionId);
    }

    @Test
    void rejectsAccessTokenForExpiredSession() {
        UUID userId = insertUser();
        UUID sessionId = UUID.randomUUID();
        insertSession(userId, sessionId, Instant.now().minusSeconds(60), null);

        assertRejectedSessionToken(userId, sessionId);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash,password_algorithm) VALUES (?,?,?,?)",
                userId, userId + "@example.test", "test-only-hash", "bcrypt");
        return userId;
    }

    private void insertSession(UUID userId, UUID sessionId, Instant expiresAt, Instant revokedAt) {
        jdbcTemplate.update("INSERT INTO user_refresh_session"
                        + " (id,user_id,family_id,token_hash,expires_at,revoked_at,created_at)"
                        + " VALUES (?,?,?,?,?,?,?)",
                sessionId,
                userId,
                UUID.randomUUID(),
                TokenDigests.sha256Hex(UUID.randomUUID().toString()),
                Timestamp.from(expiresAt),
                revokedAt == null ? null : Timestamp.from(revokedAt),
                Timestamp.from(Instant.now()));
    }

    private void assertRejectedSessionToken(UUID userId, UUID sessionId) {
        Instant issuedAt = Instant.now();
        Jwt token = Jwt.withTokenValue("test-access-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("sid", sessionId.toString())
                .claim(IdentityJwtClaims.AUTHORIZATION_VERSION, 1)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .build();

        assertThatThrownBy(() -> jwtAuthenticationConverter.convert(token))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(exception -> ((OAuth2AuthenticationException) exception).getError().getErrorCode())
                .isEqualTo("invalid_token");
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
                    } catch (InvalidRefreshTokenAuthenticationException | BusinessException expectedForLosingRequest) {
                        // Invalid token is the expected outcome for the request that loses the atomic update.
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
