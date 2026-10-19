package io.roseiot.auth;

import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthAttemptThrottle {
    private final AuthRateLimitMapper rateLimits;

    AuthAttemptThrottle(AuthRateLimitMapper rateLimits) {
        this.rateLimits = rateLimits;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    boolean allow(String scope, String normalizedEmail, int limit, Duration window) {
        String key = SecretDigest.sha256Hex(scope + ":" + normalizedEmail);
        return rateLimits.recordAttempt(key, window.toSeconds()) <= limit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void clear(String scope, String normalizedEmail) {
        rateLimits.clear(SecretDigest.sha256Hex(scope + ":" + normalizedEmail));
    }

    @Transactional
    void removeExpired() {
        rateLimits.deleteExpired();
    }
}
