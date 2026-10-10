package io.roseiot.shared.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlidingWindowRateLimiterTest {

    private RateLimitProperties properties() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setWindowMinutes(60);
        return properties;
    }

    @Test
    void shouldAllowAttemptsUpToTheLimit() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties());

        assertThatCode(() -> {
            cut.checkAllowed("login:a@b.co", 3);
            cut.checkAllowed("login:a@b.co", 3);
            cut.checkAllowed("login:a@b.co", 3);
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTheAttemptAboveTheLimit() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties());
        cut.checkAllowed("login:a@b.co", 1);

        assertThatThrownBy(() -> cut.checkAllowed("login:a@b.co", 1))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void shouldIsolateKeysSoOneAccountCannotLockOutAnother() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties());
        cut.checkAllowed("login:a@b.co", 1);

        assertThatCode(() -> cut.checkAllowed("login:z@b.co", 1)).doesNotThrowAnyException();
    }

    @Test
    void shouldApplyTheAttemptLimitOfEachRuleToItsOwnKeySpace() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties());
        cut.checkAllowed("recovery:a@b.co", 2);
        cut.checkAllowed("recovery:a@b.co", 2);

        assertThatThrownBy(() -> cut.checkAllowed("recovery:a@b.co", 2))
                .isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> cut.checkAllowed("recovery:a@b.co", 5)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectRepeatedlyOnceTheWindowIsFull() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties());
        cut.checkAllowed("login:a@b.co", 1);

        // A blocked caller must stay blocked, not get one free attempt per try.
        assertThatThrownBy(() -> cut.checkAllowed("login:a@b.co", 1))
                .isInstanceOf(RateLimitExceededException.class);
        assertThatThrownBy(() -> cut.checkAllowed("login:a@b.co", 1))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void shouldReclaimExpiredKeysDuringPeriodicCleanup() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties());
        Instant start = Instant.parse("2026-10-09T00:00:00Z");
        cut.checkAllowed("old-key", 1, start);

        for (int index = 0; index < 62; index++) {
            cut.checkAllowed("fresh-key-" + index, 1, start);
        }
        cut.checkAllowed("current-key", 1, start.plusSeconds(61 * 60L));

        assertThat(cut.trackedKeyCount()).isEqualTo(1);
    }
}
