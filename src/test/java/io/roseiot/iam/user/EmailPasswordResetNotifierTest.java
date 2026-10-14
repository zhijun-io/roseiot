package io.roseiot.iam.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.roseiot.config.PasswordResetEmailProperties;
import io.roseiot.system.SystemSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailPasswordResetNotifierTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SystemSettingService settings = mock(SystemSettingService.class);
    private final EmailPasswordResetNotifier notifier = notifier();

    @Test
    void sendsPasswordResetMessageWithLinkAndApplicationSender() {
        when(settings.requireGeneralBaseUrl()).thenReturn("https://roseiot.example/");

        notifier.sendPasswordResetEmail(new PasswordResetRequestedEvent("person@example.com", "one-time-token"));

        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@roseiot.example");
        assertThat(message.getValue().getTo()).containsExactly("person@example.com");
        assertThat(message.getValue().getText())
                .contains("https://roseiot.example/password-resets")
                .contains("Reset token: one-time-token")
                .contains("If you did not request this");
        assertThat(message.getValue().getText()).doesNotContain("#token=");
    }

    @Test
    void mailFailureDoesNotEscapeTheAfterCommitListener() {
        when(settings.requireGeneralBaseUrl()).thenReturn("https://roseiot.example");
        doThrow(new IllegalStateException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> notifier.sendPasswordResetEmail(
                        new PasswordResetRequestedEvent("person@example.com", "one-time-token")))
                .doesNotThrowAnyException();
    }

    private EmailPasswordResetNotifier notifier() {
        return new EmailPasswordResetNotifier(
                mailSender, settings, new PasswordResetEmailProperties(true, "no-reply@roseiot.example"));
    }
}
