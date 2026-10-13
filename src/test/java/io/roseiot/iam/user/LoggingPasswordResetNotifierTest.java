package io.roseiot.iam.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.roseiot.system.SystemSettingService;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class LoggingPasswordResetNotifierTest {

    @Test
    void isNotCreatedOutsideLocalProfileEvenWhenPropertyIsEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(NotifierConfiguration.class)
                .withPropertyValues("roseiot.password-reset.logging.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(LoggingPasswordResetNotifier.class));
    }

    @Test
    void isCreatedOnlyWhenLocalProfileAndOptInPropertyAreBothPresent() {
        new ApplicationContextRunner()
                .withUserConfiguration(NotifierConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues("roseiot.password-reset.logging.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(LoggingPasswordResetNotifier.class));
    }

    @Test
    void keepsResetTokenOutOfTheHttpRequestPathAndQuery() {
        URI link = URI.create(LoggingPasswordResetNotifier.createLink("https://roseiot.example/", "abc_DEF-123"));

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

    @Configuration(proxyBeanMethods = false)
    @Import(LoggingPasswordResetNotifier.class)
    static class NotifierConfiguration {

        @Bean
        SystemSettingService systemSettingService() {
            return mock(SystemSettingService.class);
        }
    }
}
