package io.roseiot.iam.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginEmailsTest {

    @Test
    void shouldTrimAndLowercaseStoredLogin() {
        assertThat(LoginEmails.normalize("  Ops@RoseIoT.example  ")).isEqualTo("ops@roseiot.example");
    }

    @Test
    void shouldMapNullToEmptyStringSoLookupsMissInsteadOfThrowing() {
        assertThat(LoginEmails.normalize(null)).isEmpty();
    }

    @Test
    void shouldFoldUpperCaseAsciiWithRootLocale() {
        assertThat(LoginEmails.normalize("ADMIN@EXAMPLE.COM")).isEqualTo("admin@example.com");
        assertThat(LoginEmails.normalize("I")).isEqualTo("i");
    }

    @Test
    void shouldBeIdempotentForAlreadyNormalizedEmail() {
        String normalized = LoginEmails.normalize("admin@roseiot.io");

        assertThat(LoginEmails.normalize(normalized)).isEqualTo(normalized);
    }

    @Test
    void shouldNotInventAnEmailFromWhitespaceOnlyInput() {
        assertThat(LoginEmails.normalize("   \t \n")).isEmpty();
    }
}
