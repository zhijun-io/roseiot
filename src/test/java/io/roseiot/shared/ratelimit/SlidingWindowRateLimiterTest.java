package io.roseiot.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

    private RateLimitProperties properties() {
        return new RateLimitProperties(60, 16 * 1024, 10_000, java.util.List.of());
    }

    @Test
    void shouldAllowAttemptsUpToTheLimit() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties(), Clock.systemUTC());

        assertThatCode(() -> {
                    cut.checkAllowed("login:a@b.co", 3);
                    cut.checkAllowed("login:a@b.co", 3);
                    cut.checkAllowed("login:a@b.co", 3);
                })
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTheAttemptAboveTheLimit() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties(), Clock.systemUTC());
        cut.checkAllowed("login:a@b.co", 1);

        assertThatThrownBy(() -> cut.checkAllowed("login:a@b.co", 1)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void shouldIsolateKeysSoOneAccountCannotLockOutAnother() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties(), Clock.systemUTC());
        cut.checkAllowed("login:a@b.co", 1);

        assertThatCode(() -> cut.checkAllowed("login:z@b.co", 1)).doesNotThrowAnyException();
    }

    @Test
    void shouldApplyTheAttemptLimitOfEachRuleToItsOwnKeySpace() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties(), Clock.systemUTC());
        cut.checkAllowed("recovery:a@b.co", 2);
        cut.checkAllowed("recovery:a@b.co", 2);

        assertThatThrownBy(() -> cut.checkAllowed("recovery:a@b.co", 2)).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> cut.checkAllowed("recovery:a@b.co", 5)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectRepeatedlyOnceTheWindowIsFull() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties(), Clock.systemUTC());
        cut.checkAllowed("login:a@b.co", 1);

        // A blocked caller must stay blocked, not get one free attempt per try.
        assertThatThrownBy(() -> cut.checkAllowed("login:a@b.co", 1)).isInstanceOf(RateLimitExceededException.class);
        assertThatThrownBy(() -> cut.checkAllowed("login:a@b.co", 1)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void shouldReportWhenTheRateLimitWindowAllowsAnotherAttempt() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties(), Clock.systemUTC());
        Instant start = Instant.parse("2026-10-09T00:00:00Z");
        cut.checkAllowed("login:a@b.co", 1, start);

        assertThatThrownBy(() -> cut.checkAllowed("login:a@b.co", 1, start.plusSeconds(30 * 60L)))
                .isInstanceOfSatisfying(
                        RateLimitExceededException.class,
                        exception -> assertThat(exception.retryAfterSeconds()).isEqualTo(30 * 60L));
    }

    @Test
    void shouldReclaimExpiredKeysDuringPeriodicCleanup() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties(), Clock.systemUTC());
        Instant start = Instant.parse("2026-10-09T00:00:00Z");
        cut.checkAllowed("old-key", 1, start);

        for (int index = 0; index < 62; index++) {
            cut.checkAllowed("fresh-key-" + index, 1, start);
        }
        cut.checkAllowed("current-key", 1, start.plusSeconds(61 * 60L));

        assertThat(cut.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void shouldRejectNewKeysWhenTheConfiguredCapacityIsReached() {
        RateLimitProperties properties = withMaxTrackedKeys(1);
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties, Clock.systemUTC());
        Instant start = Instant.parse("2026-10-09T00:00:00Z");
        cut.checkAllowed("first", 5, start);

        assertThatThrownBy(() -> cut.checkAllowed("second", 5, start.plusSeconds(1)))
                .isInstanceOf(RateLimitCapacityExceededException.class);
        assertThat(cut.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void shouldReuseCapacityAfterEntriesExpire() {
        RateLimitProperties properties = withMaxTrackedKeys(1);
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties, Clock.systemUTC());
        Instant start = Instant.parse("2026-10-09T00:00:00Z");
        cut.checkAllowed("first", 5, start);

        assertThatCode(() -> cut.checkAllowed("second", 5, start.plusSeconds(61 * 60L)))
                .doesNotThrowAnyException();
        assertThat(cut.trackedKeyCount()).isEqualTo(1);
    }

    private RateLimitProperties withMaxTrackedKeys(int maxTrackedKeys) {
        return new RateLimitProperties(60, 16 * 1024, maxTrackedKeys, java.util.List.of());
    }
}
