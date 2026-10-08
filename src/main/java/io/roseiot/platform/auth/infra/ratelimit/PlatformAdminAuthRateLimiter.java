package io.roseiot.platform.auth.infra.ratelimit;

import io.roseiot.platform.auth.config.PlatformAuthRateLimitProperties;
import io.roseiot.platform.auth.domain.AuthRateLimitExceededException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
@ConditionalOnProperty(name = "roseiot.bootstrap.enabled", havingValue = "false", matchIfMissing = true)
public class PlatformAdminAuthRateLimiter {

    private final PlatformAuthRateLimitProperties properties;
    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    public PlatformAdminAuthRateLimiter(PlatformAuthRateLimitProperties properties) {
        this.properties = properties;
    }

    public void onLoginAttempt(String normalizedEmail) {
        assertWithinLimit("login:" + normalizedEmail, properties.getLoginMaxAttempts());
    }

    public void onRecoveryRequest(String normalizedEmail) {
        assertWithinLimit("recovery:" + normalizedEmail, properties.getRecoveryMaxAttempts());
    }

    private void assertWithinLimit(String key, int maxAttempts) {
        if (!properties.isEnabled()) {
            return;
        }
        Instant cutoff = Instant.now().minus(properties.getWindowMinutes(), ChronoUnit.MINUTES);
        Deque<Instant> attempts = attemptsByKey.computeIfAbsent(key, ignored -> new ConcurrentLinkedDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
                attempts.pollFirst();
            }
            if (attempts.size() >= maxAttempts) {
                throw new AuthRateLimitExceededException();
            }
            attempts.addLast(Instant.now());
        }
    }
}
