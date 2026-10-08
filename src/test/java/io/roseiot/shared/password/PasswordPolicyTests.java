package io.roseiot.shared.password;

import io.roseiot.identity.domain.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTests {

    @Test
    void acceptsValidPassword() {
        assertThat(PasswordPolicy.validationError("password1234")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "123456789012", "abcdefghijkl"})
    void rejectsInvalidPassword(String password) {
        assertThat(PasswordPolicy.validationError(password)).isPresent();
    }
}
