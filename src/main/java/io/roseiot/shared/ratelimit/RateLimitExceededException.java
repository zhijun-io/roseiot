package io.roseiot.shared.ratelimit;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException() {
        this(1);
    }

    public RateLimitExceededException(Duration retryAfter) {
        this(Math.max(1, (retryAfter.toMillis() + 999) / 1_000));
    }

    private RateLimitExceededException(long retryAfterSeconds) {
        super("rate_limited");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
