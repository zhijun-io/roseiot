package io.roseiot.iam.user;

import io.roseiot.config.PasswordResetEmailProperties;
import io.roseiot.system.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(name = "roseiot.password-reset.email.enabled", havingValue = "true")
class EmailPasswordResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailPasswordResetNotifier.class);

    private final JavaMailSender mailSender;
    private final SystemSettingService systemSettingService;
    private final String from;

    EmailPasswordResetNotifier(
            JavaMailSender mailSender,
            SystemSettingService systemSettingService,
            PasswordResetEmailProperties properties) {
        this.mailSender = mailSender;
        this.systemSettingService = systemSettingService;
        this.from = properties.from();
    }

    @TransactionalEventListener
    @Async("emailNotificationExecutor")
    public void sendPasswordResetEmail(PasswordResetRequestedEvent event) {
        try {
            String link = PasswordResetLink.create(systemSettingService.requireGeneralBaseUrl());
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(event.email());
            message.setSubject("Reset your Roseiot password");
            message.setText("A password reset was requested for your Roseiot account.\n\n"
                    + "Open this page within one hour and enter the one-time reset token below:\n" + link
                    + "\n\nReset token: " + event.token()
                    + "\n\nIf you did not request this, you can ignore this email.");
            mailSender.send(message);
        } catch (RuntimeException exception) {
            log.error("Password reset email delivery failed", exception);
        }
    }
}
