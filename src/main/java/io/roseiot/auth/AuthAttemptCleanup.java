package io.roseiot.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class AuthAttemptCleanup {
    private final AuthAttemptThrottle throttle;

    AuthAttemptCleanup(AuthAttemptThrottle throttle) {
        this.throttle = throttle;
    }

    @Scheduled(fixedDelayString = "PT1H")
    void cleanupExpiredEntries() {
        throttle.removeExpired();
    }
}
