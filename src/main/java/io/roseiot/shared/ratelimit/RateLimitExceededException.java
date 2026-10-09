package io.roseiot.shared.ratelimit;

/** Request rejected by {@link SlidingWindowRateLimiter}; mapped to HTTP 429 at the API boundary. */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super("rate_limited");
    }
}
