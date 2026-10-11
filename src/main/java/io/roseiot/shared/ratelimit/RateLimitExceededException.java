package io.roseiot.shared.ratelimit;

public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super("rate_limited");
    }
}
