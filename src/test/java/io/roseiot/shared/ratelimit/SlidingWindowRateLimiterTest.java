package io.roseiot.shared.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlidingWindowRateLimiterTest {

    private RateLimitProperties properties(boolean enabled) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(enabled);
        properties.setWindowMinutes(60);
        return properties;
    }

    @Test
    void shouldAllowAttemptsUpToTheLimit() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties(true));

        assertThatCode(() -> {
            cut.checkAllowed("login:a@b.co", 3);
            cut.checkAllowed("login:a@b.co", 3);
            cut.checkAllowed("login:a@b.co", 3);
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTheAttemptAboveTheLimit() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties(true));
        cut.checkAllowed("login:a@b.co", 1);

        assertThatThrownBy(() -> cut.checkAllowed("login:a@b.co", 1))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void shouldIsolateKeysSoOneAccountCannotLockOutAnother() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties(true));
        cut.checkAllowed("login:a@b.co", 1);

        assertThatCode(() -> cut.checkAllowed("login:z@b.co", 1)).doesNotThrowAnyException();
    }

    @Test
    void shouldApplyTheAttemptLimitOfEachRuleToItsOwnKeySpace() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties(true));
        cut.checkAllowed("recovery:a@b.co", 2);
        cut.checkAllowed("recovery:a@b.co", 2);

        assertThatThrownBy(() -> cut.checkAllowed("recovery:a@b.co", 2))
                .isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> cut.checkAllowed("recovery:a@b.co", 5)).doesNotThrowAnyException();
    }

    @Test
    void shouldNotLimitAnythingWhenDisabled() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties(false));

        for (int attempt = 0; attempt < 50; attempt++) {
            assertThatCode(() -> cut.checkAllowed("login:a@b.co", 1)).doesNotThrowAnyException();
        }
    }

    @Test
    void shouldRejectRepeatedlyOnceTheWindowIsFull() {
        SlidingWindowRateLimiter cut = new SlidingWindowRateLimiter(properties(true));
        cut.checkAllowed("login:a@b.co", 1);

        // A blocked caller must stay blocked, not get one free attempt per try.
        assertThatThrownBy(() -> cut.checkAllowed("login:a@b.co", 1))
                .isInstanceOf(RateLimitExceededException.class);
        assertThatThrownBy(() -> cut.checkAllowed("login:a@b.co", 1))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
