package io.roseiot.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformPasswordPolicyTests {

    @Test
    void acceptsValidPassword() {
        assertThat(PlatformPasswordPolicy.validationError("password1234")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "123456789012", "abcdefghijkl"})
    void rejectsInvalidPassword(String password) {
        assertThat(PlatformPasswordPolicy.validationError(password)).isPresent();
    }
}
