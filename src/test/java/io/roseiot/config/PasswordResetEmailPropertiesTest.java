package io.roseiot.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordResetEmailPropertiesTest {

    @Test
    void requiresSafeSenderOnlyWhenEmailIsEnabled() {
        new PasswordResetEmailProperties(false, "");

        assertThatThrownBy(() -> new PasswordResetEmailProperties(true, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new PasswordResetEmailProperties(true, "attacker@example.com\r\nBcc: victim@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
