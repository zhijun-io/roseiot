package io.roseiot.iam.user;

import io.roseiot.system.SystemSettingService;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoggingPasswordResetNotifierTest {

    @Test
    void keepsResetTokenOutOfTheHttpRequestPathAndQuery() {
        URI link = URI.create(LoggingPasswordResetNotifier.createLink(
                "https://roseiot.example/", "abc_DEF-123"));

        assertThat(link.getPath()).isEqualTo("/password-resets");
        assertThat(link.getQuery()).isNull();
        assertThat(link.getFragment()).isEqualTo("token=abc_DEF-123");
    }

    @Test
    void deliveryFailureDoesNotChangePasswordResetRequestOutcome() {
        SystemSettingService settings = mock(SystemSettingService.class);
        when(settings.requireGeneralBaseUrl()).thenThrow(new IllegalStateException("setting unavailable"));
        LoggingPasswordResetNotifier notifier = new LoggingPasswordResetNotifier(settings);

        assertThatCode(() -> notifier.sendPasswordResetEmail(
                new PasswordResetRequestedEvent("person@example.com", "one-time-secret")))
                .doesNotThrowAnyException();
    }
}
