package io.roseiot.iam.domain;

import io.roseiot.shared.domain.ApiErrorCode;
import io.roseiot.shared.domain.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void shouldAcceptPasswordWithLettersAndDigits() {
        assertThatCode(() -> PasswordPolicy.ensureValid("correct horse 42", ApiErrorCode.VALIDATION_ERROR))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectPasswordBelowMinimumLength() {
        assertInvalid("ab12", "Password must be at least 12 characters");
    }

    @Test
    void shouldAcceptPasswordAtMinimumLength() {
        assertThatCode(() -> PasswordPolicy.ensureValid("abcdefghij12", ApiErrorCode.VALIDATION_ERROR))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" has no letter or no digit")
    @ValueSource(strings = {"123456789012", "!@#$%^&*()_-+=[]{}", "  　  　  1 2 3 "})
    void shouldRejectPasswordWithoutLetterOrDigit(String password) {
        assertInvalid(password, "Password must contain letters and digits");
    }

    @Test
    void shouldRejectNullPasswordWithMinimumLengthMessage() {
        assertInvalid(null, "Password must be at least 12 characters");
    }

    @Test
    void shouldReportLengthBeforeMissingCharacterClasses() {
        // A short password that also lacks digits must surface one rule, and the length rule wins.
        assertInvalid("abcdefgh", "Password must be at least 12 characters");
    }

    private static void assertInvalid(String password, String message) {
        assertThatThrownBy(() -> PasswordPolicy.ensureValid(password, ApiErrorCode.VALIDATION_ERROR))
                .isInstanceOf(BusinessException.class)
                .hasMessage(message);
    }
}
