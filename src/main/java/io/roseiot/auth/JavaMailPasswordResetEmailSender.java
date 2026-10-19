package io.roseiot.auth;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class JavaMailPasswordResetEmailSender implements PasswordResetEmailSender {
    private final JavaMailSender mailSender;
    private final AuthMailProperties properties;

    JavaMailPasswordResetEmailSender(JavaMailSender mailSender, AuthMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String recipient, String resetUrl) {
        var message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(recipient);
        message.setSubject("Roseiot password reset");
        message.setText("Use this one-time link to reset your Roseiot password:\n\n" + resetUrl
                + "\n\nThe link expires soon and can only be used once.");
        mailSender.send(message);
    }
}
