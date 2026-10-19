package io.roseiot.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
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
        String key = digest(scope + ":" + normalizedEmail);
        return rateLimits.recordAttempt(key, window.toSeconds()) <= limit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void clear(String scope, String normalizedEmail) {
        rateLimits.clear(digest(scope + ":" + normalizedEmail));
    }

    @Transactional
    void removeExpired() {
        rateLimits.deleteExpired();
    }

    private String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
