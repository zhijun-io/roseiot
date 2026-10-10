package io.roseiot.shared.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class SlidingWindowRateLimiter {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();
    private final AtomicInteger cleanupCounter = new AtomicInteger();

    public SlidingWindowRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    public void checkAllowed(String key, int maxAttempts) {
        checkAllowed(key, maxAttempts, Instant.now());
    }

    void checkAllowed(String key, int maxAttempts, Instant now) {
        Instant cutoff = now.minus(properties.getWindowMinutes(), ChronoUnit.MINUTES);
        attemptsByKey.compute(key, (ignored, attempts) -> {
            Deque<Instant> activeAttempts = attempts == null ? new ConcurrentLinkedDeque<>() : attempts;
            synchronized (activeAttempts) {
                discardExpired(activeAttempts, cutoff);
                if (activeAttempts.size() >= maxAttempts) {
                    throw new RateLimitExceededException();
                }
                activeAttempts.addLast(now);
            }
            return activeAttempts;
        });
        if ((cleanupCounter.incrementAndGet() & 63) == 0) {
            attemptsByKey.forEach((existingKey, attempts) -> attemptsByKey.computeIfPresent(
                    existingKey, (ignored, current) -> {
                        synchronized (current) {
                            discardExpired(current, cutoff);
                            return current.isEmpty() ? null : current;
                        }
                    }));
        }
    }

    private static void discardExpired(Deque<Instant> attempts, Instant cutoff) {
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.pollFirst();
        }
    }

    int trackedKeyCount() {
        return attemptsByKey.size();
    }
}
