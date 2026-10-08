package io.roseiot.shared.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class SlidingWindowRateLimiter {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    public void checkAllowed(String key, int maxAttempts) {
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
                throw new RateLimitExceededException();
            }
            attempts.addLast(Instant.now());
        }
    }
}
