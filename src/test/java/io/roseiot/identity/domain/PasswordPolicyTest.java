package io.roseiot.identity.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void shouldAcceptPasswordWithLettersAndDigits() {
        assertThat(PasswordPolicy.validationError("correct horse 42")).isEmpty();
    }

    @Test
    void shouldRejectPasswordBelowMinimumLength() {
        Optional<String> error = PasswordPolicy.validationError("ab12");

        assertThat(error).contains("Password must be at least 12 characters");
    }

    @Test
    void shouldAcceptPasswordAtMinimumLength() {
        assertThat(PasswordPolicy.validationError("abcdefghij12")).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" has no letter or no digit")
    @ValueSource(strings = {"123456789012", "!@#$%^&*()_-+=[]{}", "  　  　  1 2 3 "})
    void shouldRejectPasswordWithoutLetterOrDigit(String password) {
        Optional<String> error = PasswordPolicy.validationError(password);

        assertThat(error).contains("Password must contain letters and digits");
    }

    @Test
    void shouldRejectNullPasswordWithMinimumLengthMessage() {
        assertThat(PasswordPolicy.validationError(null))
                .contains("Password must be at least 12 characters");
    }

    @Test
    void shouldReportLengthBeforeMissingCharacterClasses() {
        // A short password that also lacks digits must surface one rule, and the length rule wins.
        assertThat(PasswordPolicy.validationError("abcdefgh"))
                .contains("Password must be at least 12 characters");
    }
}
