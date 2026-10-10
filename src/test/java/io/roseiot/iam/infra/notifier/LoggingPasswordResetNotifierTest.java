package io.roseiot.iam.infra.notifier;

import io.roseiot.system.application.SystemSettingService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoggingPasswordResetNotifierTest {

    private final SystemSettingService settings = mock(SystemSettingService.class);
    private final LoggingPasswordResetNotifier notifier = new LoggingPasswordResetNotifier(settings);

    @Test
    void appendsResetPathToBasePathAndEncodesToken() {
        assertThat(LoggingPasswordResetNotifier.createLink("https://roseiot.example/prefix/", "a+b /"))
                .isEqualTo("https://roseiot.example/prefix/password-resets/a%2Bb%20%2F");
    }

    @Test
    void rejectsRelativeNonHttpAndQueryBaseUrls() {
        assertThatThrownBy(() -> LoggingPasswordResetNotifier.createLink("/relative", "token"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LoggingPasswordResetNotifier.createLink("ftp://roseiot.example", "token"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LoggingPasswordResetNotifier.createLink("https://roseiot.example?x=1", "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void readsBaseUrlFromGeneralSystemSetting() {
        when(settings.requireGeneralBaseUrl()).thenReturn("https://roseiot.example");

        notifier.sendPasswordResetEmail("admin@roseiot.example", "reset-token");

        verify(settings).requireGeneralBaseUrl();
    }

    @Test
    void failsWhenGeneralBaseUrlIsNotConfigured() {
        when(settings.requireGeneralBaseUrl()).thenThrow(new IllegalStateException("GENERAL setting baseUrl is required"));

        assertThatThrownBy(() -> notifier.sendPasswordResetEmail("admin@roseiot.example", "reset-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("GENERAL setting baseUrl is required");
    }
}
