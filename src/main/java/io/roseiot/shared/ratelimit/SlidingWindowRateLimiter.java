package io.roseiot.shared.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SlidingWindowRateLimiter {

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attemptsByKey = new HashMap<>();
    private final AtomicInteger cleanupCounter = new AtomicInteger();
    private Instant lastCapacityCleanup;

    public SlidingWindowRateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void checkAllowed(String key, int maxAttempts) {
        checkAllowed(key, maxAttempts, Instant.now(clock));
    }

    synchronized void checkAllowed(String key, int maxAttempts, Instant now) {
        Instant cutoff = now.minus(properties.windowMinutes(), ChronoUnit.MINUTES);
        Deque<Instant> activeAttempts = attemptsByKey.get(key);
        if (activeAttempts == null) {
            if (attemptsByKey.size() >= properties.maxTrackedKeys()) {
                cleanupExpiredKeys(cutoff, now);
                if (attemptsByKey.size() >= properties.maxTrackedKeys()) {
                    throw new RateLimitCapacityExceededException();
                }
            }
            activeAttempts = new ArrayDeque<>();
            attemptsByKey.put(key, activeAttempts);
        }
        discardExpired(activeAttempts, cutoff);
        if (activeAttempts.size() >= maxAttempts) {
            Instant firstAttemptExpiresAt = activeAttempts.peekFirst()
                    .plus(properties.windowMinutes(), ChronoUnit.MINUTES);
            throw new RateLimitExceededException(Duration.between(now, firstAttemptExpiresAt));
        }
        activeAttempts.addLast(now);
        if ((cleanupCounter.incrementAndGet() & 63) == 0) {
            cleanupExpiredKeys(cutoff, now);
        }
    }

    private void cleanupExpiredKeys(Instant cutoff, Instant now) {
        if (lastCapacityCleanup != null && now.isBefore(lastCapacityCleanup.plusSeconds(30))) return;
        Iterator<Map.Entry<String, Deque<Instant>>> entries = attemptsByKey.entrySet().iterator();
        while (entries.hasNext()) {
            Deque<Instant> attempts = entries.next().getValue();
            discardExpired(attempts, cutoff);
            if (attempts.isEmpty()) entries.remove();
        }
        lastCapacityCleanup = now;
    }

    private static void discardExpired(Deque<Instant> attempts, Instant cutoff) {
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.pollFirst();
        }
    }

    synchronized int trackedKeyCount() {
        return attemptsByKey.size();
    }
}
