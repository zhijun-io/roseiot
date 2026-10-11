package io.roseiot.shared.ratelimit;

public class RateLimitCapacityExceededException extends RuntimeException {

    public RateLimitCapacityExceededException() {
        super("Rate limit tracking capacity is exhausted");
    }
}
